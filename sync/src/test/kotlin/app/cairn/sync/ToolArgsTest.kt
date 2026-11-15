package app.cairn.sync

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgsTest {
    private val today = LocalDate.of(2030, 1, 4)
    private fun s(t: String): JsonObject = Json.parseToJsonElement(t).jsonObject

    @Test fun daysAndLimitByName() {
        val b = ToolArgs.build(s("""{"properties":{"days":{"type":"integer"},"limit":{"type":"integer"}}}"""), ArgRequest(days = 7, limit = 5), today)
        assertEquals(7, b.args["days"]!!.jsonPrimitive.int)
        assertEquals(5, b.args["limit"]!!.jsonPrimitive.int)
    }

    @Test fun isoDatesWhenFormatSaysSo() {
        val b = ToolArgs.build(s("""{"properties":{"start_date":{"type":"string","format":"date"},"end_date":{"type":"string","format":"date"}}}"""), ArgRequest(days = 3), today)
        assertEquals("2030-01-02", b.args["start_date"]!!.jsonPrimitive.content)
        assertEquals("2030-01-04", b.args["end_date"]!!.jsonPrimitive.content)
    }

    @Test fun optionalUnknownFormatIsOmittedRequiredIsUnresolved() {
        val optional = ToolArgs.build(s("""{"properties":{"startDate":{"type":"string"}}}"""), ArgRequest(days = 3), today)
        assertTrue(optional.args.isEmpty() && optional.unresolved.isEmpty())
        val required = ToolArgs.build(s("""{"properties":{"startDate":{"type":"string"}},"required":["startDate"]}"""), ArgRequest(days = 3), today)
        assertEquals(listOf("startDate"), required.unresolved)
    }

    @Test fun noSchemaMeansNoArguments() {
        val b = ToolArgs.build(null, ArgRequest(days = 7), today)
        assertTrue(b.args.isEmpty() && b.unresolved.isEmpty())
    }

    @Test fun integerDatesAreYyyymmdd() {
        val b = ToolArgs.build(s("""{"properties":{"endDay":{"type":"integer"}}}"""), ArgRequest(days = 1), today)
        assertEquals(20300104, b.args["endDay"]!!.jsonPrimitive.int)
    }
}
