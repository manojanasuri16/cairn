package app.cairn.sync

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** What the caller wants, independent of how a tool names its arguments. */
data class ArgRequest(val days: Int? = null, val limit: Int? = null)

/** [unresolved] lists required properties that could not be filled; the engine skips such a call. */
data class BuiltArgs(val args: JsonObject, val unresolved: List<String>)

/**
 * Builds `tools/call` arguments from the tool's `inputSchema` (from `tools/list`), never from assumptions about
 * a specific server: properties are recognised by name family (days / limit / start / end), date formats are
 * taken from the schema (`format`, `pattern`, `description`, integer type). An optional property whose format
 * cannot be determined is omitted (server default applies); a required one makes the call unresolved.
 */
object ToolArgs {
    private val DAYS = setOf("days", "daycount", "numdays", "ndays", "lookbackdays", "dayrange", "numberofdays")
    private val LIMIT = setOf("limit", "pagesize", "count", "maxresults", "top", "size")
    private val START = setOf("startdate", "startday", "start", "from", "fromdate", "begindate", "begin")
    private val END = setOf("enddate", "endday", "end", "to", "todate")
    private val BASIC = DateTimeFormatter.BASIC_ISO_DATE

    fun build(schema: JsonObject?, req: ArgRequest, today: LocalDate): BuiltArgs {
        val props = schema?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
        val required = (schema?.get("required") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        val values = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        val unresolved = mutableListOf<String>()
        for ((name, rawDef) in props) {
            val def = rawDef as? JsonObject ?: JsonObject(emptyMap())
            val norm = name.lowercase().replace("_", "").replace("-", "")
            val value: kotlinx.serialization.json.JsonElement? = when (norm) {
                in DAYS -> req.days?.let { number(def, it) }
                in LIMIT -> req.limit?.let { number(def, it) }
                in START -> req.days?.let { date(def, today.minusDays((it - 1).toLong())) }
                in END -> req.days?.let { date(def, today) }
                else -> null
            }
            if (value != null) values[name] = value
            else if (name in required && "default" !in def) unresolved += name
        }
        return BuiltArgs(buildJsonObject { values.forEach { (k, v) -> put(k, v) } }, unresolved)
    }

    private fun number(def: JsonObject, n: Int): JsonPrimitive =
        if ((def["type"] as? JsonPrimitive)?.contentOrNull == "string") JsonPrimitive(n.toString()) else JsonPrimitive(n)

    private fun date(def: JsonObject, d: LocalDate): JsonPrimitive? {
        val type = (def["type"] as? JsonPrimitive)?.contentOrNull
        val hint = listOf("format", "pattern", "description", "title").joinToString(" ") { (def[it] as? JsonPrimitive)?.contentOrNull.orEmpty() }.lowercase()
        return when {
            "yyyymmdd" in hint || "\\d{8}" in hint || "[0-9]{8}" in hint ->
                if (type == "integer") JsonPrimitive(d.format(BASIC).toInt()) else JsonPrimitive(d.format(BASIC))
            "yyyy-mm-dd" in hint || (def["format"] as? JsonPrimitive)?.contentOrNull == "date" -> JsonPrimitive(d.toString())
            type == "integer" -> JsonPrimitive(d.format(BASIC).toInt()) // an integer date can only be YYYYMMDD in practice
            else -> null
        }
    }
}
