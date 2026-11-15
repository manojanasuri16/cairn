package app.cairn.source.corosmcp.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Parsed value plus non-fatal [warnings]. Parsers never throw on odd input; they return what they could read. */
data class ParseResult<out T>(val value: T, val warnings: List<String> = emptyList())

/** The text of an MCP `tools/call` result and whether the server flagged it as an error. */
data class ToolReply(val text: String, val isError: Boolean = false) {
    companion object {
        /** Joins all `content[].text` parts of a tool result. Tolerates a missing/odd shape. */
        fun from(result: JsonObject): ToolReply {
            val parts = (result["content"] as? JsonArray).orEmpty().mapNotNull {
                ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
            }
            val err = (result["isError"] as? JsonPrimitive)?.booleanOrNull ?: false
            return ToolReply(unwrapQuoted(parts.joinToString("\n")), err)
        }

        /**
         * COROS returns each report as a JSON string literal inside the MCP text part: the text starts and ends with
         * a double quote and its line breaks are the two characters `\n`. Decode it back to plain multi-line text;
         * anything that is not a well-formed quoted string is returned unchanged.
         */
        internal fun unwrapQuoted(text: String): String {
            val t = text.trim()
            if (t.length < 2 || t.first() != '"' || t.last() != '"') return text
            return runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(t) as? JsonPrimitive)?.takeIf { it.isString }?.content }
                .getOrNull() ?: text
        }
    }
}

/** Shared low-level helpers: label/value splitting and unit-tolerant number parsing. */
internal object Txt {
    private val LABEL = Regex("""^\s*([A-Za-z0-9][^:|]*?)\s*:\s*(.*?)\s*$""")
    private val NUM = Regex("""-?\d[\d,]*(?:\.\d+)?""")
    private val HOURS = Regex("""(\d+)\s*h(?:ours?|rs?)?\b""", RegexOption.IGNORE_CASE)
    private val MINS = Regex("""(\d+)\s*(?:min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b""")
    private const val KM_PER_MILE = 1.609344

    /** "A: x | B: y" -> [(a, x), (b, y)] with lower-cased labels. Segments without a label are skipped. */
    fun segments(line: String): List<Pair<String, String>> =
        line.split('|').mapNotNull { seg -> LABEL.find(seg)?.let { it.groupValues[1].lowercase() to it.groupValues[2] } }

    /** Label map for a block of lines; the first occurrence of a label wins. */
    fun labels(lines: List<String>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        lines.forEach { l -> segments(l).forEach { (k, v) -> m.putIfAbsent(k, v) } }
        return m
    }

    fun int(s: String?): Int? = s?.let { NUM.find(it) }?.value?.replace(",", "")?.toDoubleOrNull()?.toInt()
    fun double(s: String?): Double? = s?.let { NUM.find(it) }?.value?.replace(",", "")?.toDoubleOrNull()

    /** "7h 30min", "53 min", "12h", "45m" -> minutes. Null when neither unit is present. */
    fun minutes(s: String?): Int? {
        if (s == null) return null
        val h = HOURS.find(s)?.groupValues?.get(1)?.toInt()
        // Remove the hour part so "5h" cannot be mistaken for minutes.
        val rest = if (h != null) HOURS.replaceFirst(s, " ") else s
        val m = MINS.find(rest)?.groupValues?.get(1)?.toInt()
        return if (h == null && m == null) null else (h ?: 0) * 60 + (m ?: 0)
    }

    /** "54:37" (mm:ss) or "1:58:07" (h:mm:ss) -> seconds. */
    fun clockSeconds(s: String?): Int? {
        val m = s?.let { CLOCK.find(it) } ?: return null
        val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val c = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
        return if (c == null) a * 60 + b else a * 3600 + b * 60 + c
    }

    /** Seconds per km from "9:50 /km" or "9:50 /mi" (converted). No unit means /km. */
    fun paceSecPerKm(s: String?): Int? {
        val sec = clockSeconds(s) ?: return null
        val perMile = s!!.contains("/mi", ignoreCase = true)
        return if (perMile) Math.round(sec / KM_PER_MILE).toInt() else sec
    }

    /** "5.55 km", "800 m", "1.5 mi", "2,000 meters" -> meters. Null without a recognised unit. */
    fun distanceMeters(s: String?): Double? {
        if (s == null) return null
        val v = double(s) ?: return null
        val unit = Regex("""[A-Za-z]+""").find(s.substring(NUM.find(s)!!.range.last + 1))?.value?.lowercase() ?: return null
        return when (unit) {
            "km", "kms", "kilometer", "kilometers", "kilometre", "kilometres" -> v * 1000
            "m", "meter", "meters", "metre", "metres" -> v
            "mi", "mile", "miles" -> v * KM_PER_MILE * 1000
            else -> null
        }
    }
}
