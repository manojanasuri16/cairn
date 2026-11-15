package app.cairn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.cairn.core.datastore.TokenStore
import app.cairn.source.corosmcp.AuthorizationException
import app.cairn.source.corosmcp.BounceRedirect
import app.cairn.source.corosmcp.CallbackCheck
import app.cairn.source.corosmcp.CallbackParams
import app.cairn.source.corosmcp.CallbackParser
import app.cairn.source.corosmcp.CallbackValidator
import app.cairn.source.corosmcp.CorosNetworkException
import app.cairn.source.corosmcp.FailureClassifier
import app.cairn.source.corosmcp.FailureReason
import app.cairn.source.corosmcp.LoopbackReceiver
import app.cairn.source.corosmcp.OAuthClient
import app.cairn.source.corosmcp.PendingStore
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.RegionPolicy
import app.cairn.source.corosmcp.SignInDiag
import app.cairn.source.corosmcp.SignInEvent
import app.cairn.source.corosmcp.SignInMachine
import app.cairn.source.corosmcp.SignInMethod
import app.cairn.source.corosmcp.SignInState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

/**
 * Process-wide owner of the sign-in flow (lives in [AppGraph], so it survives the screen, the ViewModel and
 * the redirect activity). The UI only observes [state]. Secrets (codes, verifiers, state values, tokens) are never logged
 * or put into [diag].
 */
class SignInCoordinator(
    context: Context,
    private val oauth: OAuthClient,
    private val tokens: TokenStore,
    private val pending: PendingStore,
    private val regionStore: RegionStore,
    private val scope: CoroutineScope,
    private val diagFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onSignedIn: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val redirectLock = Mutex()

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state

    private val _diag = MutableStateFlow(loadDiag())
    val diag: StateFlow<SignInDiag> = _diag

    private val _resumedAt = MutableStateFlow<Long?>(null)
    /** Last time the app came to the foreground; drives the gentle "no result yet" hint. */
    val resumedAt: StateFlow<Long?> = _resumedAt

    @Volatile private var attemptJob: Job? = null
    @Volatile private var exchangeJob: Job? = null
    @Volatile private var ticker: Job? = null
    @Volatile private var receiver: LoopbackReceiver? = null
    @Volatile private var localUrl: String? = null

    init {
        val rec = pending.load()
        val restored = SignInMachine.restore(tokens.load() != null, RegionPolicy.start(regionStore.get()), rec, clock())
        if (rec != null && (restored !is SignInState.WaitingForBrowser)) pending.clear()
        _state.value = restored
        if (restored is SignInState.WaitingForBrowser) startTicker()
    }

    fun markResumed() { _resumedAt.value = clock() }

    // ---------------- public actions ----------------

    /** Starts (or restarts) a sign-in; whatever was pending is cancelled cleanly first. */
    fun start(method: SignInMethod, region: Region, forced: Boolean = false) {
        if (_state.value is SignInState.Exchanging) return
        cleanup(cancelTicker = true, cancelExchange = true)
        dispatch(SignInEvent.Start(method, region))
        note(method, "starting", null)
        attemptJob = scope.launch {
            try {
                when (method) {
                    SignInMethod.BOUNCE -> runBounce(region, forced)
                    SignInMethod.LOCAL -> runLocal(region, forced)
                    SignInMethod.DEVICE -> runDevice(region)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, method, region)
            }
        }
    }

    fun cancel() {
        cleanup(cancelTicker = true, cancelExchange = false)
        dispatch(SignInEvent.Cancel)
        note(null, "cancelled", null)
    }

    /** Re-opens the same authorize URL while the pending record (or local receiver) is still valid. */
    fun reopenBrowser() {
        val s = _state.value as? SignInState.WaitingForBrowser ?: return
        val url = when (s.method) {
            SignInMethod.BOUNCE -> pending.load()?.takeIf { !it.expired(clock()) }?.authorizeUrl
            SignInMethod.LOCAL -> localUrl
            SignInMethod.DEVICE -> null
        }
        if (url == null) { dispatch(SignInEvent.Failure(FailureReason.EXPIRED, s.region)); note(s.method, "reopen", FailureReason.EXPIRED.code); return }
        try { launchBrowser(url); note(s.method, "browser reopened", null) } catch (e: Exception) { fail(e, s.method, s.region) }
    }

    fun onSignedOut() {
        cleanup(cancelTicker = true, cancelExchange = true)
        dispatch(SignInEvent.SignedOut)
        note(null, "signed out", null)
    }

    /** Called by [OAuthRedirectActivity] with the raw `app.cairn.oauth://callback?...` URI. Survives the activity. */
    fun handleRedirect(uri: String?) {
        val p = CallbackParser.parse(uri) as? CallbackParams.Result ?: return
        exchangeJob = scope.launch { processRedirect(p) }
    }

    // ---------------- flows ----------------

    private suspend fun runBounce(region: Region, forced: Boolean) {
        val p = oauth.beginAuthorization(region, BounceRedirect.REDIRECT_URI)
        val rec = oauth.toRecord(p, region, clock(), forced)
        pending.save(rec) // BEFORE the browser opens: the process may be killed while Chrome is in front
        note(SignInMethod.BOUNCE, "registered", null)
        launchBrowser(rec.authorizeUrl)
        dispatch(SignInEvent.BrowserLaunched(clock()))
        note(SignInMethod.BOUNCE, "browser launched", null)
        startTicker()
    }

    private suspend fun runLocal(startRegion: Region, forced: Boolean) {
        var region = startRegion
        try {
            while (true) {
                try {
                    LoopbackReceiver.open().use { rx ->
                        receiver = rx
                        val p = oauth.beginAuthorization(region, rx.redirectUri)
                        SignInService.start(appContext)
                        localUrl = p.authorizeUrl
                        launchBrowser(p.authorizeUrl)
                        dispatch(SignInEvent.BrowserLaunched(clock()))
                        note(SignInMethod.LOCAL, "browser launched", null)
                        startTicker()
                        val code = rx.awaitCode(p.state)
                        note(SignInMethod.LOCAL, "redirect received", null)
                        dispatch(SignInEvent.ExchangeStarted(region))
                        retryOnce { withTimeout(EXCHANGE_TIMEOUT_MS) { oauth.exchangeCodeFor(p, code) } }
                    }
                    success(region, SignInMethod.LOCAL)
                    return
                } catch (e: AuthorizationException) {
                    val next = RegionPolicy.next(region)
                    if (!forced && next != null && RegionPolicy.isWrongRegion(e)) {
                        region = next
                        dispatch(SignInEvent.Start(SignInMethod.LOCAL, region))
                        continue
                    }
                    throw e
                }
            }
        } finally {
            receiver = null
            SignInService.stop(appContext)
        }
    }

    private suspend fun runDevice(region: Region) {
        val (flow, resp) = oauth.startDeviceFlow(region)
        launchBrowser(resp.verificationUriComplete ?: resp.verificationUri)
        dispatch(SignInEvent.BrowserLaunched(clock(), deviceCode = resp.userCode))
        note(SignInMethod.DEVICE, "browser launched", null)
        startTicker()
        flow.awaitApproval()
        success(region, SignInMethod.DEVICE)
    }

    private suspend fun processRedirect(p: CallbackParams.Result) = redirectLock.withLock {
        val now = clock()
        val rec = pending.load()
        val st = _state.value
        if (rec == null && (st is SignInState.SignedIn || st is SignInState.Exchanging)) return // duplicate delivery
        note(SignInMethod.BOUNCE, "redirect received", null)
        val check = CallbackValidator.check(rec, p, now)
        if (check !is CallbackCheck.Accept) {
            pending.clear()
            cleanup(cancelTicker = true, cancelExchange = false)
            val region = rec?.region
            if (check is CallbackCheck.ProviderError && check.wrongRegion && rec != null && !rec.forced) {
                val next = RegionPolicy.next(rec.region)
                if (next != null) {
                    exchangeJob = null // do not cancel ourselves
                    note(SignInMethod.BOUNCE, "region fallback", check.code.take(40))
                    start(SignInMethod.BOUNCE, next)
                    return
                }
            }
            val reason = FailureClassifier.fromCheck(check) ?: FailureReason.OTHER
            dispatch(SignInEvent.Failure(reason, region))
            note(SignInMethod.BOUNCE, "failed", if (check is CallbackCheck.ProviderError) "${reason.code}:${check.code.take(40)}" else reason.code)
            return
        }
        rec!!
        pending.clear() // single use from here: the verifier now lives only in memory
        ticker?.cancel()
        dispatch(SignInEvent.ExchangeStarted(rec.region))
        note(SignInMethod.BOUNCE, "exchanging", null)
        try {
            retryOnce { withTimeout(EXCHANGE_TIMEOUT_MS) { oauth.exchangeCode(rec, check.code) } }
            success(rec.region, SignInMethod.BOUNCE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e, SignInMethod.BOUNCE, rec.region)
        }
    }

    // ---------------- plumbing ----------------

    private fun success(region: Region, method: SignInMethod) {
        ticker?.cancel()
        regionStore.set(region)
        pending.clear()
        dispatch(SignInEvent.Succeeded)
        if (_state.value !is SignInState.SignedIn) _state.value = SignInState.SignedIn(region) // device flow: Waiting -> SignedIn
        note(method, "signed in", null)
        onSignedIn()
    }

    private fun fail(e: Throwable, method: SignInMethod, region: Region) {
        val reason = FailureClassifier.classify(e)
        pending.clear()
        ticker?.cancel()
        dispatch(SignInEvent.Failure(reason, region))
        val detail = when (e) {
            is AuthorizationException -> "${reason.code}:${e.code.take(40)}"
            is app.cairn.source.corosmcp.CorosException -> "${reason.code}:${e.message.orEmpty().take(80)}"
            else -> "${reason.code}:${e.javaClass.simpleName}"
        }
        note(method, "failed", detail)
    }

    /** Cancels the running attempt, the loopback receiver and the foreground service, and deletes the pending record. */
    private fun cleanup(cancelTicker: Boolean, cancelExchange: Boolean) {
        attemptJob?.cancel(); attemptJob = null
        if (cancelExchange) { exchangeJob?.cancel(); exchangeJob = null }
        if (cancelTicker) { ticker?.cancel(); ticker = null }
        runCatching { receiver?.close() }; receiver = null
        localUrl = null
        SignInService.stop(appContext)
        pending.clear()
    }

    private fun dispatch(e: SignInEvent) { _state.update { SignInMachine.reduce(it, e) } }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                dispatch(SignInEvent.Tick(clock()))
                val s = _state.value
                if (s is SignInState.Failed) {
                    note(null, "failed", FailureReason.TIMEOUT.code)
                    cleanup(cancelTicker = false, cancelExchange = false)
                    return@launch
                }
                if (s !is SignInState.WaitingForBrowser) return@launch
            }
        }
    }

    private fun launchBrowser(url: String) {
        CustomTabsIntent.Builder().build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(appContext, Uri.parse(url))
    }

    private suspend fun <T> retryOnce(block: suspend () -> T): T = try {
        block()
    } catch (e: CorosNetworkException) {
        delay(800); block()
    } catch (e: TimeoutCancellationException) {
        delay(800); block()
    }

    private fun note(method: SignInMethod?, stage: String, failure: String?) {
        val prev = _diag.value
        val d = SignInDiag(method?.label ?: prev.method, stage, failure ?: prev.failure, clock())
        _diag.value = d
        runCatching { diagFile.parentFile?.mkdirs(); diagFile.writeText(json.encodeToString(SignInDiag.serializer(), d)) }
    }

    private fun loadDiag(): SignInDiag =
        runCatching { json.decodeFromString(SignInDiag.serializer(), diagFile.readText()) }.getOrDefault(SignInDiag())

    private companion object {
        const val TICK_MS = 5_000L
        const val EXCHANGE_TIMEOUT_MS = 25_000L
    }
}

/** Local method needs the PendingAuthorization's own tokens exchange; kept next to the coordinator to avoid widening OAuthClient. */
private suspend fun OAuthClient.exchangeCodeFor(p: app.cairn.source.corosmcp.PendingAuthorization, code: String) = completeAuthorization(p, code)
