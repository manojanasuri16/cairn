package app.cairn

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.datastore.EncryptedFileTokenStore
import app.cairn.core.datastore.KeystoreAesGcmCipher
import app.cairn.source.corosmcp.CorosException
import app.cairn.source.corosmcp.LoopbackReceiver
import app.cairn.source.corosmcp.McpClient
import app.cairn.source.corosmcp.OAuthClient
import app.cairn.source.corosmcp.Redactor
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.RegionPolicy
import app.cairn.source.corosmcp.Shape
import app.cairn.source.corosmcp.SignInMessages
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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
)

val INTERESTING_TOOLS = listOf(
    "queryDailyHealthData", "queryRecoveryStatus", "querySleepOverview", "querySleepHrv",
    "queryTrainingLoadAssessment", "queryFitnessAssessmentOverview", "querySportRecords",
)

class CorosViewModel(app: Application) : AndroidViewModel(app) {
    // No logging interceptor at all: nothing can leak Authorization headers or bodies.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    // files/datastore/ is excluded from cloud backup and device transfer.
    private val store = EncryptedFileTokenStore(File(app.filesDir, "datastore/coros_tokens.bin"), KeystoreAesGcmCipher())
    private val oauth = OAuthClient(http, store)
    private var mcp: McpClient? = null
    private var job: Job? = null

    /** Last raw result is held in memory only, for the redacted copy action; cleared on sign-out. */
    private var lastRaw: kotlinx.serialization.json.JsonObject? = null

    // Region is not secret; kept in a plain app-private file next to (not inside) the token blob.
    private val regionFile = File(app.filesDir, "datastore/coros_region.txt")
    private fun cachedRegion(): Region? = runCatching { Region.valueOf(regionFile.readText().trim()) }.getOrNull()
    private fun cacheRegion(r: Region) { runCatching { regionFile.parentFile?.mkdirs(); regionFile.writeText(r.name) } }
    private fun statusSignedIn(r: Region) = "Signed in (${r.label})"

    private val _state = run {
        val signed = oauth.isSignedIn()
        val r = RegionPolicy.start(cachedRegion())
        MutableStateFlow(CorosUiState(region = r, signedIn = signed, status = if (signed) statusSignedIn(r) else "Not signed in"))
    }
    val state: StateFlow<CorosUiState> = _state

    fun setRegion(r: Region) = _state.update { it.copy(region = r) }

    private fun client(): McpClient = mcp ?: McpClient(http, oauth, McpClient.endpointFor(_state.value.region)).also { mcp = it }

    private fun run(label: String, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(busy = true, status = label) }
            try { block() } catch (e: CorosException) {
                _state.update { it.copy(status = e.message ?: "Error") }
            } catch (e: Exception) {
                _state.update { it.copy(status = "Error: ${e.javaClass.simpleName}") }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }

    /**
     * Region choice: start at the cached region (else US). If the callback returns an error code that
     * clearly means "account not on this server" (RegionPolicy.isWrongRegion) we advance US -> EU -> CN
     * automatically. Any other failure (cancel, timeout, network, loopback) stops and is shown, with a
     * "Try another region" action that moves to the next region. [forced] (Advanced chips) skips fallback.
     */
    fun signIn(context: Context, start: Region = _state.value.region, forced: Boolean = false) = run("Opening COROS sign-in") {
        var region = start
        while (true) {
            mcp = null
            _state.update { it.copy(region = region, failedRegion = null) }
            try {
                attempt(context, region)
                cacheRegion(region)
                _state.update { it.copy(signedIn = true, failedRegion = null, status = statusSignedIn(region)) }
                return@run
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val next = RegionPolicy.next(region)
                if (!forced && next != null && RegionPolicy.isWrongRegion(e)) { region = next; continue }
                _state.update { it.copy(failedRegion = region) }
                throw e
            }
        }
    }

    /** User-initiated: continue with the region after the one that just failed. */
    fun tryAnotherRegion(context: Context) {
        val failed = _state.value.failedRegion ?: return
        val next = RegionPolicy.next(failed)
        if (next == null) _state.update { it.copy(status = "No more regions to try") } else signIn(context, next)
    }

    private suspend fun attempt(context: Context, region: Region) {
        LoopbackReceiver.open().use { rx ->
            val pending = oauth.beginAuthorization(region, rx.redirectUri)
            CustomTabsIntent.Builder().build().apply {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.launchUrl(context, Uri.parse(pending.authorizeUrl))
            _state.update { it.copy(status = "Waiting for sign-in in browser (${region.label})", signInHint = SignInMessages.HINT) }
            val code = try { rx.awaitCode(pending.state) } finally { _state.update { it.copy(signInHint = null) } }
            oauth.completeAuthorization(pending, code)
        }
    }

    fun signInDeviceCode(context: Context) = run("Starting device sign-in") {
        mcp = null
        val (flow, resp) = oauth.startDeviceFlow(_state.value.region)
        _state.update { it.copy(deviceCode = resp.userCode, status = "Enter the code on the COROS page") }
        CustomTabsIntent.Builder().build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(context, Uri.parse(resp.verificationUriComplete ?: resp.verificationUri))
        flow.awaitApproval()
        cacheRegion(_state.value.region)
        _state.update { it.copy(signedIn = true, deviceCode = null, status = statusSignedIn(it.region)) }
    }

    fun signOut() = run("Signing out") {
        try { oauth.signOut() } finally {
            mcp = null; lastRaw = null
            _state.update { it.copy(signedIn = false, tools = emptyList(), shape = null, canCopy = false, deviceCode = null, status = "Signed out") }
        }
    }

    fun listTools() = run("Listing tools") {
        val names = client().listTools()
        _state.update { it.copy(tools = names, status = "${names.size} tools") }
    }

    fun call(tool: String) = run("Calling $tool") {
        val result = client().callTool(tool)
        lastRaw = result
        _state.update { it.copy(shape = tool to Shape.describe(result), canCopy = true, status = "Done") }
    }

    /** Redacted JSON of the last call, for the clipboard. Nothing is written to disk. */
    fun redactedSample(): String? = lastRaw?.let { Redactor.redactResult(it) }
}
