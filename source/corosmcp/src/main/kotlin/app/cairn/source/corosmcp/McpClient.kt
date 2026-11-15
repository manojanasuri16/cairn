package app.cairn.source.corosmcp

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Minimal MCP Streamable HTTP client (JSON-RPC). Handles JSON or SSE bodies and an optional session id. */
class McpClient(
    private val http: OkHttpClient,
    private val auth: TokenProvider,
    private val endpoint: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val ids = AtomicLong(1)
    @Volatile private var sessionId: String? = null
    @Volatile private var initialized = false

    companion object {
        const val PROTOCOL = "2025-06-18"
        fun endpointFor(region: Region) = region.host + "/mcp"
    }

    suspend fun initialize() {
        val params = buildJsonObject {
            put("protocolVersion", PROTOCOL)
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject { put("name", "Cairn"); put("version", "0.1.0") })
        }
        rpc("initialize", params)
        notify("notifications/initialized")
        initialized = true
    }

    suspend fun listTools(): List<String> {
        ensureInit()
        val result = rpc("tools/list", buildJsonObject {})
        return (result["tools"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("name")?.let { n -> (n as? JsonPrimitive)?.content } }
    }

    /** Returns the raw `result` object of tools/call. Callers must not persist or log it. */
    suspend fun callTool(name: String, arguments: JsonObject = buildJsonObject {}): JsonObject {
        ensureInit()
        return rpc("tools/call", buildJsonObject { put("name", name); put("arguments", arguments) })
    }

    private suspend fun ensureInit() { if (!initialized) initialize() }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        val id = ids.getAndIncrement()
        val body = buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params) }
        val text = post(body.toString(), expectBody = true)
        val msg = McpParsing.extractResponse(text, id) ?: throw CorosException("No JSON-RPC response for $method")
        (msg["error"] as? JsonObject)?.let { e ->
            throw CorosException("MCP error ${(e["code"] as? JsonPrimitive)?.content ?: "?"} on $method")
        }
        return (msg["result"] as? JsonObject) ?: JsonObject(emptyMap())
    }

    private suspend fun notify(method: String) {
        val body = buildJsonObject { put("jsonrpc", "2.0"); put("method", method) }
        post(body.toString(), expectBody = false)
    }

    /** POST with one refresh-and-retry on 401. */
    private suspend fun post(payload: String, expectBody: Boolean): String {
        var token = auth.accessToken() ?: throw CorosException("Not signed in")
        var retried = false
        while (true) {
            val r = send(payload, token)
            if (r.code == 401 && !retried) {
                retried = true
                if (!auth.refreshAfter401(token)) throw CorosException("Session expired; sign in again")
                token = auth.accessToken() ?: throw CorosException("Not signed in")
                continue
            }
            if (r.code == 401) throw CorosException("Unauthorized after refresh")
            if (r.code !in 200..299) throw CorosException("MCP HTTP ${r.code}")
            return r.body
        }
    }

    private class Resp(val code: Int, val body: String)

    private suspend fun send(payload: String, token: String): Resp = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL)
            .post(payload.toRequestBody("application/json".toMediaType()))
        sessionId?.let { b.header("Mcp-Session-Id", it) }
        try {
            http.newCall(b.build()).execute().use { r ->
                r.header("Mcp-Session-Id")?.let { sessionId = it }
                Resp(r.code, r.body?.string().orEmpty())
            }
        } catch (e: java.io.IOException) {
            throw CorosException("Network error: ${e.javaClass.simpleName}")
        }
    }
}

object McpParsing {
    private val json = Json { ignoreUnknownKeys = true }

    /** Accepts a plain JSON body or an SSE stream; returns the JSON-RPC message with [id] (or the only response). */
    fun extractResponse(body: String, id: Long): JsonObject? {
        val trimmed = body.trimStart()
        val messages: List<JsonElement> =
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) listOf(json.parseToJsonElement(trimmed))
            else parseSse(body).mapNotNull { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        val flat = messages.flatMap { if (it is JsonArray) it.toList() else listOf(it) }.mapNotNull { it as? JsonObject }
        return flat.firstOrNull { (it["id"] as? JsonPrimitive)?.content == id.toString() }
            ?: flat.firstOrNull { it.containsKey("result") || it.containsKey("error") }
    }

    /** Returns the data payload of each SSE event (multi-line data joined with newlines). */
    fun parseSse(body: String): List<String> {
        val events = mutableListOf<String>()
        val cur = StringBuilder()
        var has = false
        for (line in body.lines() + "") {
            if (line.isEmpty()) {
                if (has) events += cur.toString()
                cur.clear(); has = false
            } else if (line.startsWith("data:")) {
                if (has) cur.append('\n')
                cur.append(line.removePrefix("data:").removePrefix(" ")); has = true
            }
        }
        return events
    }
}
