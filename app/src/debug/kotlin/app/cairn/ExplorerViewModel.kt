package app.cairn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.source.corosmcp.ExplorerPolicy
import app.cairn.source.corosmcp.McpClient
import app.cairn.source.corosmcp.Redactor
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.SchemaSummary
import app.cairn.source.corosmcp.Shape
import app.cairn.source.corosmcp.ToolInfo
import app.cairn.source.corosmcp.parse.ToolReply
import app.cairn.sync.ArgRequest
import app.cairn.sync.ToolArgs
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ExplorerTool(val info: ToolInfo, val schemaLines: List<String>, val blockReason: String?) {
    val callable get() = blockReason == null
}

data class ExplorerResult(val shape: String, val sample: String, val note: String? = null)

data class ExplorerState(
    val tools: List<ExplorerTool> = emptyList(),
    val results: Map<String, ExplorerResult> = emptyMap(),
    val progress: Map<String, String> = emptyMap(), // tool -> OK / error text
    val busy: Boolean = false,
    val status: String = "Not loaded",
)

/** Debug-only. Never logs tokens; replies are redacted before being shown or written to files/explorer. */
class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    private val _state = MutableStateFlow(ExplorerState())
    val state: StateFlow<ExplorerState> = _state
    private val client: McpClient by lazy { McpClient(graph.http, graph.oauth, McpClient.endpointFor(graph.regionStore.get() ?: Region.US)) }
    private val outDir get() = File(getApplication<Application>().filesDir, "explorer")

    private fun launch(label: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true, status = label) }
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(status = "Error: ${e.message ?: e.javaClass.simpleName}") }
        } finally { _state.update { it.copy(busy = false) } }
    }

    fun load() { launch("Listing tools") {
        val infos = client.listToolInfo()
        val tools = infos.map { ExplorerTool(it, SchemaSummary.summarize(it.inputSchema), ExplorerPolicy.blockReason(it.name)) }
        _state.update { it.copy(tools = tools, status = "${tools.size} tools, ${tools.count { t -> t.callable }} callable") }
    } }

    fun call(tool: String) { launch("Calling $tool") { callOne(tool, persist = false) } }

    /** Latest cached activity, used to fill id-like arguments. */
    private suspend fun latestActivity() = graph.database.activities().observeSince("1970-01-01").first().firstOrNull()

    private suspend fun callOne(name: String, persist: Boolean): String {
        // Hard gate, independent of the UI.
        require(ExplorerPolicy.isCallable(name)) { "blocked" }
        val t = _state.value.tools.first { it.info.name == name }
        val built = ToolArgs.build(t.info.inputSchema, ArgRequest(days = 7, limit = 5), LocalDate.now())
        val args = LinkedHashMap(built.args)
        val missing = mutableListOf<String>()
        if (built.unresolved.isNotEmpty()) {
            val act = latestActivity()
            for (p in built.unresolved) {
                val n = p.lowercase().replace("_", "")
                val v = when {
                    act == null -> null
                    n in setOf("labelid", "activityid", "id") -> JsonPrimitive(act.id)
                    n == "sporttype" -> act.sportType?.let { JsonPrimitive(it) }
                    else -> null
                }
                if (v != null) args[p] = v else missing += p
            }
        }
        if (missing.isNotEmpty()) {
            val msg = "skipped, needs ${missing.joinToString()}" + if (latestActivity() == null) " (no cached activity)" else ""
            _state.update { it.copy(progress = it.progress + (name to msg), results = it.results + (name to ExplorerResult("", "", msg))) }
            return msg
        }
        val result = client.callTool(name, buildJsonObject { args.forEach { (k, v) -> put(k, v) } })
        val text = Redactor.redactText(ToolReply.from(result).text)
        val res = ExplorerResult(Shape.describe(result), text.take(4000))
        if (persist) {
            outDir.mkdirs()
            File(outDir, "$name.txt").writeText(text)
        }
        _state.update { it.copy(results = it.results + (name to res), progress = it.progress + (name to "OK")) }
        return "OK"
    }

    fun captureAll() { launch("Capturing") {
        val tools = _state.value.tools
        outDir.mkdirs()
        val schemas = buildJsonObject {
            tools.forEach { t -> put(t.info.name, buildJsonObject { put("description", t.info.description); put("inputSchema", t.info.inputSchema) }) }
        }
        File(outDir, "_schemas.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), schemas))
        _state.update { it.copy(progress = emptyMap()) }
        val todo = tools.filter { it.callable }
        todo.forEachIndexed { i, t ->
            _state.update { it.copy(status = "Capturing ${i + 1}/${todo.size}: ${t.info.name}") }
            try { callOne(t.info.name, persist = true) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(progress = it.progress + (t.info.name to "error: ${e.message ?: e.javaClass.simpleName}")) }
            }
            if (i < todo.lastIndex) delay(1500)
        }
        _state.update { it.copy(status = "Done. Files in ${outDir.path}") }
    } }
}
