package app.cairn.source.corosmcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Produces a shareable fixture: identifying fields, emails, ids, coordinates and long numeric ids are masked,
 * timestamps are rounded to the hour. Health metrics (sleep minutes, HRV, load) are kept so parsers can be tested.
 * Output is meant for the user to review before sharing.
 */
object Redactor {
    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { prettyPrint = true }

    private val maskKeys = Regex(
        "(name|email|mail|phone|avatar|photo|birth|address|token|nick|serial|place|location)|^(lat|lon|lng|latitude|longitude|userid|openid|uid|sn|mac|imei|deviceid|devicesn|city|country)$",
        RegexOption.IGNORE_CASE,
    )
    private val idKeys = Regex("(^id$|Id$|Uuid|uuid)")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val longDigits = Regex("\\b\\d{9,}\\b")
    private val coordPair = Regex("-?\\d{1,3}\\.\\d{4,}")
    private val isoTime = Regex("(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}):\\d{2}(:\\d{2}(\\.\\d+)?)?")

    fun redactResult(result: JsonObject): String =
        prettyJson.encodeToString(JsonElement.serializer(), redactElement(null, result))

    fun redactElement(key: String?, e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (k, v) -> redactElement(k, v) })
        is JsonArray -> JsonArray(e.map { redactElement(key, it) })
        is JsonNull -> e
        is JsonPrimitive -> redactPrimitive(key, e)
    }

    private fun redactPrimitive(key: String?, p: JsonPrimitive): JsonElement {
        if (key != null && (maskKeys.containsMatchIn(key) || idKeys.containsMatchIn(key))) {
            return if (p.isString) JsonPrimitive("<masked>") else JsonPrimitive(0)
        }
        if (p.isString) {
            val parsed = runCatching { json.parseToJsonElement(p.content) }.getOrNull()
            if (parsed is JsonObject || parsed is JsonArray) {
                return JsonPrimitive(prettyJson.encodeToString(JsonElement.serializer(), redactElement(key, parsed)))
            }
            return JsonPrimitive(redactText(p.content))
        }
        val l = p.content.toLongOrNull()
        if (l != null && key != null && looksLikeTimeKey(key)) return JsonPrimitive(roundTime(l))
        if (l != null && p.content.length >= 9 && !looksLikeTimeKey(key.orEmpty())) return JsonPrimitive(0)
        return p
    }

    private fun looksLikeTimeKey(k: String) = Regex("(time|date|ts|stamp|day|start|end)", RegexOption.IGNORE_CASE).containsMatchIn(k)

    private fun roundTime(v: Long): Long = when {
        v > 100_000_000_000L -> v / 3_600_000 * 3_600_000 // epoch ms
        v > 1_000_000_000L -> v / 3_600 * 3_600 // epoch s
        else -> v
    }

    /** Masks identifying patterns in free text (the "formatted text" tool outputs). */
    fun redactText(s: String): String = s
        .replace(email, "<email>")
        .replace(isoTime) { "${it.groupValues[1]}T${it.groupValues[2]}:00" }
        .replace(coordPair, "<coord>")
        .replace(longDigits, "<id>")
        .lines().joinToString("\n") { redactLine(it) }

    private val labelLine = Regex("^(\\s*)(name|nickname|user|email|device)\\s*[:=]\\s*.+$", RegexOption.IGNORE_CASE)
    private val locationInline = Regex("(location|place|venue)\\s*[:=]\\s*[^|;\\n]+", RegexOption.IGNORE_CASE)
    private val serialInline = Regex("((?:device\\s+)?(?:serial(?:\\s*(?:number|no\\.?))?|sn|imei|mac))\\s*[:=#]\\s*[A-Za-z0-9:_-]{4,}", RegexOption.IGNORE_CASE)
    private val personInline = Regex("(athlete|owner|nickname|full\\s*name|first\\s*name|last\\s*name|user\\s*name)\\s*[:=]\\s*[^|;\\n]+", RegexOption.IGNORE_CASE)

    private fun redactLine(line: String): String {
        val m = labelLine.find(line)
        if (m != null) return "${m.groupValues[1]}${m.groupValues[2]}: <masked>"
        return line
            .replace(locationInline) { "${it.groupValues[1]}: <place>" }
            .replace(serialInline) { "${it.groupValues[1]}: <serial>" }
            .replace(personInline) { "${it.groupValues[1]}: <name>" }
    }
}
