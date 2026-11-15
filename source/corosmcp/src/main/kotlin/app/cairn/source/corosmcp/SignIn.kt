package app.cairn.source.corosmcp

import app.cairn.core.datastore.BlobCipher
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SignInMethod(val label: String) { BOUNCE("Browser (HTTPS bounce)"), LOCAL("Local method"), DEVICE("Device code") }

/** The fixed HTTPS bounce redirect (a static GitHub Pages page that forwards to the app scheme). Trailing slash matters. */
object BounceRedirect {
    const val REDIRECT_URI = "https://manojanasuri16.github.io/cairn/callback/"
    const val APP_SCHEME = "app.cairn.oauth"
    const val APP_HOST = "callback"
    const val PENDING_TTL_MS = 15 * 60_000L
}

/** What the redirect (app scheme or loopback) carried. Values are never logged. */
sealed interface CallbackParams {
    class Result(val code: String?, val state: String?, val error: String?) : CallbackParams {
        override fun toString() = "CallbackParams.Result(redacted)"
    }
    data object NotACallback : CallbackParams
}

object CallbackParser {
    /** Parses `app.cairn.oauth://callback?code=..&state=..` (or any URL/path with a query). */
    fun parse(url: String?): CallbackParams {
        if (url.isNullOrBlank()) return CallbackParams.NotACallback
        val head = url.substringBefore('?').substringBefore('#')
        val isApp = head.equals("${BounceRedirect.APP_SCHEME}://${BounceRedirect.APP_HOST}", true) ||
            head.equals("${BounceRedirect.APP_SCHEME}://${BounceRedirect.APP_HOST}/", true)
        if (!isApp) return CallbackParams.NotACallback
        val q = url.substringAfter('?', "").substringBefore('#')
        val m = q.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        return CallbackParams.Result(m["code"]?.takeIf { it.isNotEmpty() }, m["state"]?.takeIf { it.isNotEmpty() }, m["error"]?.takeIf { it.isNotEmpty() })
    }
}

/** The authorization in flight, persisted (encrypted) BEFORE the browser opens so a killed process can still finish. */
@Serializable
data class PendingRecord(
    val clientId: String,
    val region: Region,
    val host: String,
    val redirectUri: String,
    val codeVerifier: String,
    val state: String,
    val createdAt: Long,
    val authorizeUrl: String,
    val tokenEndpoint: String,
    val issuer: String,
    val forced: Boolean = false,
) {
    override fun toString() = "PendingRecord(redacted)"
    fun expired(now: Long): Boolean = now - createdAt > BounceRedirect.PENDING_TTL_MS || now < createdAt - 60_000
}

interface PendingStore {
    fun load(): PendingRecord?
    fun save(r: PendingRecord)
    fun clear()
}

/** Same approach as the token store: one AES-GCM sealed blob under files/datastore/. Unreadable reads as absent. */
class EncryptedFilePendingStore(private val file: File, private val cipher: BlobCipher) : PendingStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized override fun load(): PendingRecord? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(PendingRecord.serializer(), cipher.decrypt(file.readBytes()).decodeToString())
        } catch (e: Exception) { null }
    }

    @Synchronized override fun save(r: PendingRecord) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.encrypt(json.encodeToString(PendingRecord.serializer(), r).encodeToByteArray()))
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "pending write failed" } }
    }

    @Synchronized override fun clear() { file.delete() }
}

/** Outcome of checking a callback against the persisted record. */
sealed interface CallbackCheck {
    class Accept(val code: String) : CallbackCheck { override fun toString() = "Accept" }
    /** Callback carried error=; [wrongRegion] true only for codes that clearly mean the account is on another server. */
    class ProviderError(val code: String, val wrongRegion: Boolean) : CallbackCheck
    data object NoPending : CallbackCheck
    data object Expired : CallbackCheck
    data object StateMismatch : CallbackCheck
    data object MissingState : CallbackCheck
    data object MissingCode : CallbackCheck
}

object CallbackValidator {
    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun check(record: PendingRecord?, p: CallbackParams.Result, now: Long): CallbackCheck {
        if (record == null) return CallbackCheck.NoPending
        if (record.expired(now)) return CallbackCheck.Expired
        val st = p.state ?: return CallbackCheck.MissingState
        if (!constantTimeEquals(st, record.state)) return CallbackCheck.StateMismatch
        p.error?.let { return CallbackCheck.ProviderError(it, RegionPolicy.isWrongRegion(AuthorizationException(it))) }
        return CallbackCheck.Accept(p.code ?: return CallbackCheck.MissingCode)
    }
}

/** Specific, user-presentable failure categories. */
enum class FailureReason(val message: String, val code: String) {
    NETWORK("Network error. Check your connection and try again.", "network"),
    REGION_MISMATCH("This COROS account was not found on that server. Try another region.", "region_mismatch"),
    REFUSED("COROS refused the sign-in.", "refused"),
    EXPIRED("Sign-in session expired, please start again.", "session_expired"),
    TIMEOUT("Sign-in timed out", "timeout"),
    OTHER("Sign-in failed. Please try again.", "other"),
}

object FailureClassifier {
    fun classify(e: Throwable): FailureReason = when {
        e is AuthorizationException -> if (RegionPolicy.isWrongRegion(e)) FailureReason.REGION_MISMATCH else FailureReason.REFUSED
        e is CorosNetworkException || e is java.io.IOException || e is kotlinx.coroutines.TimeoutCancellationException -> FailureReason.NETWORK
        e is SignInTimeoutException -> FailureReason.TIMEOUT
        e is CorosException && e.message == "Sign-in was denied" -> FailureReason.REFUSED
        e is CorosException && e.message == "Device code expired" -> FailureReason.EXPIRED
        e is CorosException && e.message?.contains("invalid_grant") == true -> FailureReason.EXPIRED
        e is CorosException && e.message?.startsWith("Token request failed") == true -> FailureReason.REFUSED
        else -> FailureReason.OTHER
    }

    fun fromCheck(c: CallbackCheck): FailureReason? = when (c) {
        is CallbackCheck.Accept -> null
        is CallbackCheck.ProviderError -> if (c.wrongRegion) FailureReason.REGION_MISMATCH else FailureReason.REFUSED
        CallbackCheck.NoPending, CallbackCheck.Expired -> FailureReason.EXPIRED
        CallbackCheck.StateMismatch, CallbackCheck.MissingState, CallbackCheck.MissingCode -> FailureReason.REFUSED
    }
}

// ---------------- state machine (pure) ----------------

sealed interface SignInState {
    data object Idle : SignInState
    data class Starting(val method: SignInMethod, val region: Region) : SignInState
    data class WaitingForBrowser(val method: SignInMethod, val region: Region, val startedAt: Long, val deviceCode: String? = null) : SignInState
    data class Exchanging(val region: Region) : SignInState
    data class SignedIn(val region: Region) : SignInState
    data class Failed(val reason: FailureReason, val canRetry: Boolean, val region: Region? = null) : SignInState
}

sealed interface SignInEvent {
    data class Start(val method: SignInMethod, val region: Region) : SignInEvent
    data class BrowserLaunched(val startedAt: Long, val deviceCode: String? = null) : SignInEvent
    data class ExchangeStarted(val region: Region) : SignInEvent
    data object Succeeded : SignInEvent
    data class Failure(val reason: FailureReason, val region: Region? = null) : SignInEvent
    data object Cancel : SignInEvent
    data class Tick(val now: Long) : SignInEvent
    data object SignedOut : SignInEvent
}

object SignInMachine {
    const val WATCHDOG_MS = 5 * 60_000L
    const val RESUME_HINT_MS = 20_000L

    fun reduce(s: SignInState, e: SignInEvent): SignInState = when (e) {
        // Starting always wins: it cancels whatever was pending (except a token exchange already in flight).
        is SignInEvent.Start -> if (s is SignInState.Exchanging) s else SignInState.Starting(e.method, e.region)
        is SignInEvent.BrowserLaunched -> when (s) {
            is SignInState.Starting -> SignInState.WaitingForBrowser(s.method, s.region, e.startedAt, e.deviceCode)
            is SignInState.WaitingForBrowser -> s.copy(startedAt = e.startedAt, deviceCode = e.deviceCode ?: s.deviceCode)
            else -> s
        }
        is SignInEvent.ExchangeStarted -> when (s) {
            is SignInState.Exchanging, is SignInState.SignedIn -> s
            else -> SignInState.Exchanging(e.region) // also covers a redirect that arrives after the process was restarted
        }
        SignInEvent.Succeeded -> when (s) {
            is SignInState.Exchanging -> SignInState.SignedIn(s.region)
            is SignInState.WaitingForBrowser -> SignInState.SignedIn(s.region) // device code approval has no exchange step
            is SignInState.Starting -> SignInState.SignedIn(s.region)
            else -> s
        }
        is SignInEvent.Failure -> if (s is SignInState.SignedIn) s else
            SignInState.Failed(e.reason, canRetry = true, region = e.region ?: regionOf(s))
        SignInEvent.Cancel -> when (s) {
            is SignInState.Starting, is SignInState.WaitingForBrowser, is SignInState.Failed -> SignInState.Idle
            else -> s
        }
        is SignInEvent.Tick -> if (s is SignInState.WaitingForBrowser && e.now - s.startedAt >= WATCHDOG_MS)
            SignInState.Failed(FailureReason.TIMEOUT, canRetry = true, region = s.region) else s
        SignInEvent.SignedOut -> SignInState.Idle
    }

    private fun regionOf(s: SignInState): Region? = when (s) {
        is SignInState.Starting -> s.region
        is SignInState.WaitingForBrowser -> s.region
        is SignInState.Exchanging -> s.region
        is SignInState.SignedIn -> s.region
        is SignInState.Failed -> s.region
        SignInState.Idle -> null
    }

    /** Sign in / "Having trouble?" buttons: only blocked while the token exchange is already in flight. */
    fun startEnabled(s: SignInState): Boolean = s !is SignInState.Exchanging

    fun cancelVisible(s: SignInState): Boolean = s is SignInState.Starting || s is SignInState.WaitingForBrowser

    fun isPending(s: SignInState): Boolean = s is SignInState.Starting || s is SignInState.WaitingForBrowser || s is SignInState.Exchanging

    /** Gentle hint when the app came back to the foreground and the result still hasn't arrived. Never auto-cancels. */
    fun showResumeHint(s: SignInState, resumedAt: Long?, now: Long): Boolean =
        s is SignInState.WaitingForBrowser && resumedAt != null && resumedAt >= s.startedAt && now - resumedAt >= RESUME_HINT_MS

    /** Reconstructs the state after a process restart from persisted facts. */
    fun restore(hasTokens: Boolean, region: Region, pending: PendingRecord?, now: Long): SignInState = when {
        hasTokens -> SignInState.SignedIn(region)
        pending != null && !pending.expired(now) -> SignInState.WaitingForBrowser(SignInMethod.BOUNCE, pending.region, pending.createdAt)
        else -> SignInState.Idle
    }
}

/** Secret-free record of the last attempt, shown in diagnostics. */
@Serializable
data class SignInDiag(
    val method: String? = null,
    val stage: String = "none",
    val failure: String? = null,
    val atMs: Long = 0L,
)
