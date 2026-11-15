package app.cairn.source.corosmcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Which tools the developer explorer may ever call. Pure; the only gate used by the explorer. */
object ExplorerPolicy {
    /** Read-only by name, but still refused. */
    private val denyExact = setOf("queryMenstruationCycles", "queryActivityFitFileDownloadUrls")
    private val denyPrefixes = listOf("create", "update", "schedule", "upload", "delete", "download")
    private val allowPrefixes = listOf("query", "get")

    fun isCallable(name: String): Boolean = blockReason(name) == null

    /** Null when callable, else a short human reason. */
    fun blockReason(name: String): String? = when {
        name in denyExact -> if (name == "queryMenstruationCycles") "Sensitive data" else "Returns download links"
        denyPrefixes.any { name.startsWith(it) } -> "Writes or downloads (not read-only)"
        allowPrefixes.none { name.startsWith(it) } -> "Not a query/get tool"
        else -> null
    }
}

/** Compact, value-free summary of a tool's JSON `inputSchema`. */
object SchemaSummary {
    fun summarize(schema: JsonObject?): List<String> {
        val props = schema?.get("properties") as? JsonObject ?: return emptyList()
        val required = (schema["required"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        return props.map { (name, raw) ->
            val d = raw as? JsonObject
            val type = (d?.get("type") as? JsonPrimitive)?.contentOrNull ?: (d?.get("type") as? JsonArray)?.joinToString("|") { (it as? JsonPrimitive)?.contentOrNull ?: "?" } ?: "any"
            val sb = StringBuilder(name).append(": ").append(type)
            if (name in required) sb.append(" (required)")
            (d?.get("format") as? JsonPrimitive)?.contentOrNull?.let { sb.append(" format=").append(it) }
            (d?.get("pattern") as? JsonPrimitive)?.contentOrNull?.let { sb.append(" pattern=").append(it) }
            (d?.get("enum") as? JsonArray)?.let { e -> sb.append(" enum=[").append(e.joinToString(",") { (it as? JsonPrimitive)?.contentOrNull ?: "?" }).append(']') }
            sb.toString()
        }
    }
}
