package app.cairn

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.source.corosmcp.CorosException
import app.cairn.source.corosmcp.McpClient
import app.cairn.source.corosmcp.OAuthClient
import app.cairn.source.corosmcp.Redactor
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.RegionPolicy
import app.cairn.source.corosmcp.Shape
import app.cairn.source.corosmcp.SignInMessages
import app.cairn.source.corosmcp.SignInDiag
import app.cairn.source.corosmcp.SignInMachine
import app.cairn.source.corosmcp.SignInMethod
import app.cairn.source.corosmcp.SignInState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CorosUiState(
    val region: Region = Region.US,
    val failedRegion: Region? = null, // set after a failed sign-in; enables "Try another region"
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val status: String = "Not signed in",
    val deviceCode: String? = null,
    val tools: List<String> = emptyList(),
    val shape: Pair<String, String>? = null, // tool to shape text
    val canCopy: Boolean = false,
    val signInHint: String? = null, // visible while waiting for the browser redirect
    val signIn: SignInState = SignInState.Idle,
    val resumeHint: Boolean = false, // gentle "still nothing?" hint after returning from the browser
    val needsSignIn: Boolean = false, // refresh token rejected: tokens exist but are useless
    val diag: SignInDiag = SignInDiag(),
)

/** Plain-words status line for a sign-in state. */
fun signInStatus(s: SignInState): String = when (s) {
    SignInState.Idle -> "Not signed in"
    is SignInState.Starting -> "Opening COROS sign-in"
    is SignInState.WaitingForBrowser -> if (s.method == SignInMethod.DEVICE) "Enter the code on the COROS page" else "Waiting for sign-in in browser (${s.region.label})"
    is SignInState.Exchanging -> "Finishing sign-in (${s.region.label})"
    is SignInState.SignedIn -> "Signed in (${s.region.label})"
    is SignInState.Failed -> s.reason.message
}

val INTERESTING_TOOLS = listOf(
    "queryDailyHealthData", "queryRecoveryStatus", "querySleepOverview", "querySleepHrv",
    "queryTrainingLoadAssessment", "queryFitnessAssessmentOverview", "querySportRecords",
)

class CorosViewModel(app: Application) : AndroidViewModel(app) {
    // Shared with the sync engine through the app graph (no logging interceptor: nothing can leak headers or bodies).
    private val graph = (app as CairnApp).graph
    private val http = graph.http
    private val oauth = graph.oauth
    private val coord = graph.signIn
    private var mcp: McpClient? = null
    private var job: Job? = null

    /** Last raw result is held in memory only, for the redacted copy action; cleared on sign-out. */
    private var lastRaw: kotlinx.serialization.json.JsonObject? = null

    /** Tool-explorer and region-chip state only. Sign-in state comes from the process-wide coordinator. */
    private val _local = MutableStateFlow(CorosUiState(region = RegionPolicy.start(graph.regionStore.get()), status = ""))

    private val coordView = combine(coord.state, coord.resumedAt, coord.diag) { s, r, d -> Triple(s, r, d) }
    private val clockTick = flow { while (true) { emit(System.currentTimeMillis()); delay(5_000) } }
    private val needsSignInFlow = graph.syncState.state.map { it.needsSignIn }.distinctUntilChanged()

    val state: StateFlow<CorosUiState> = combine(_local, coordView, needsSignInFlow, clockTick) { l, (s, resumed, diag), needs, now ->
        val waiting = s as? SignInState.WaitingForBrowser
        l.copy(
            region = l.region,
            failedRegion = (s as? SignInState.Failed)?.region,
            signedIn = s is SignInState.SignedIn,
            busy = l.busy || s is SignInState.Exchanging,
            status = if (l.status.isNotBlank() && (l.busy || s is SignInState.SignedIn)) l.status else signInStatus(s),
            deviceCode = waiting?.deviceCode,
            signInHint = if (waiting != null) SignInMessages.HINT else null,
            signIn = s,
            resumeHint = SignInMachine.showResumeHint(s, resumed, now),
            needsSignIn = needs && s is SignInState.SignedIn,
            diag = diag,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorosUiState(region = _local.value.region, signedIn = coord.state.value is SignInState.SignedIn, status = signInStatus(coord.state.value), signIn = coord.state.value))

    fun setRegion(r: Region) = _local.update { it.copy(region = r) }

    fun onResumed() = coord.markResumed()

    private fun client(): McpClient = mcp ?: McpClient(http, oauth, McpClient.endpointFor(_local.value.region)).also { mcp = it }

    private fun run(label: String, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _local.update { it.copy(busy = true, status = label) }
            try { block() } catch (e: CorosException) {
                _local.update { it.copy(status = e.message ?: "Error") }
            } catch (e: Exception) {
                _local.update { it.copy(status = "Error: ${e.javaClass.simpleName}") }
            } finally { _local.update { it.copy(busy = false) } }
        }
    }

    // ---- sign-in (all state lives in SignInCoordinator; these just start/cancel attempts) ----

    /**
     * Primary sign-in (HTTPS bounce). Region: cached, else US; a "wrong region" error advances US -> EU -> CN
     * automatically unless [forced] (Advanced chips). Starting again cancels any pending attempt.
     */
    @Suppress("UNUSED_PARAMETER")
    fun signIn(context: Context, start: Region = _local.value.region, forced: Boolean = false) {
        mcp = null; coord.start(SignInMethod.BOUNCE, start, forced)
    }

    /** Fallback A: loopback receiver inside a short-lived foreground service. */
    fun signInLocal(forced: Boolean = false) { mcp = null; coord.start(SignInMethod.LOCAL, _local.value.region, forced) }

    /** Fallback B: device-code login. */
    @Suppress("UNUSED_PARAMETER")
    fun signInDeviceCode(context: Context) { mcp = null; coord.start(SignInMethod.DEVICE, _local.value.region, true) }

    fun cancelSignIn() = coord.cancel()
    fun reopenSignInPage() = coord.reopenBrowser()

    /** User-initiated: continue with the region after the one that just failed. */
    @Suppress("UNUSED_PARAMETER")
    fun tryAnotherRegion(context: Context) {
        val failed = (coord.state.value as? SignInState.Failed)?.region ?: return
        RegionPolicy.next(failed)?.let { setRegion(it); coord.start(SignInMethod.BOUNCE, it, true) }
    }

    fun signOut() = run("Signing out") {
        try { oauth.signOut() } finally {
            graph.onSignedOut()
            coord.onSignedOut()
            mcp = null; lastRaw = null
            _local.update { it.copy(tools = emptyList(), shape = null, canCopy = false, status = "") }
        }
    }

    fun listTools() = run("Listing tools") {
        val names = client().listTools()
        _local.update { it.copy(tools = names, status = "${names.size} tools") }
    }

    fun call(tool: String) = run("Calling $tool") {
        val result = client().callTool(tool)
        lastRaw = result
        _local.update { it.copy(shape = tool to Shape.describe(result), canCopy = true, status = "Done") }
    }

    /** Redacted JSON of the last call, for the clipboard. Nothing is written to disk. */
    fun redactedSample(): String? = lastRaw?.let { Redactor.redactResult(it) }
}
