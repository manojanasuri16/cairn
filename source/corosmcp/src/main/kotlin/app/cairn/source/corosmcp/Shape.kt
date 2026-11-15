package app.cairn.source.corosmcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Describes the structure of a tools/call result without exposing values. */
object Shape {
    private val json = Json { ignoreUnknownKeys = true }

    fun describe(result: JsonObject): String {
        val sb = StringBuilder()
        val content = result["content"] as? JsonArray
        sb.append("isError=").append((result["isError"] as? JsonPrimitive)?.content ?: "false").append('\n')
        sb.append("content items=").append(content?.size ?: 0).append('\n')
        content?.forEachIndexed { i, item ->
            val o = item as? JsonObject ?: return@forEachIndexed
            val type = (o["type"] as? JsonPrimitive)?.content
            val text = (o["text"] as? JsonPrimitive)?.content
            sb.append("[$i] type=$type")
            if (text != null) {
                sb.append(" textLen=${text.length}")
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
                if (parsed is JsonObject || parsed is JsonArray) {
                    sb.append(" json\n"); describeElement(parsed, sb, 1, 3)
                } else {
                    sb.append(" plain-text lines=${text.lines().size}\n")
                }
            } else sb.append('\n')
        }
        (result["structuredContent"] as? JsonElement)?.let { sb.append("structuredContent:\n"); describeElement(it, sb, 1, 3) }
        return sb.toString().trimEnd()
    }

    private fun describeElement(e: JsonElement, sb: StringBuilder, depth: Int, max: Int) {
        val pad = "  ".repeat(depth)
        when (e) {
            is JsonObject -> e.forEach { (k, v) ->
                when (v) {
                    is JsonObject -> { sb.append("$pad$k: object(${v.size} keys)\n"); if (depth < max) describeElement(v, sb, depth + 1, max) }
                    is JsonArray -> {
                        sb.append("$pad$k: array(${v.size})\n")
                        v.firstOrNull()?.let { if (depth < max && it is JsonObject) describeElement(it, sb, depth + 1, max) }
                    }
                    is JsonNull -> sb.append("$pad$k: null\n")
                    is JsonPrimitive -> sb.append("$pad$k: ${if (v.isString) "string(len ${v.content.length})" else "number/bool"}\n")
                }
            }
            is JsonArray -> {
                sb.append("${pad}array(${e.size})\n")
                e.firstOrNull()?.let { if (depth < max) describeElement(it, sb, depth + 1, max) }
            }
            else -> Unit
        }
    }
}
