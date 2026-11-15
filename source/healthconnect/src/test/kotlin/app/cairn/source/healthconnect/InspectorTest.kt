package app.cairn.source.healthconnect

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorTest {
    private val t0 = Instant.parse("2026-11-01T10:00:00Z")

    @Test fun summarizeCountsLatestAndDistinctOrigins() {
        val s = RecordAggregator.summarize(
            listOf(
                RecordMeta(t0, "a"),
                RecordMeta(t0.plusSeconds(60), "b"),
                RecordMeta(t0.plusSeconds(30), "a"),
            ),
        )
        assertEquals(3, s.count)
        assertEquals(t0.plusSeconds(60), s.latestModified)
        assertEquals(setOf("a", "b"), s.origins)
    }

    @Test fun emptyHasNoLatestOrOrigins() {
        val s = RecordAggregator.summarize(emptyList())
        assertEquals(0, s.count)
        assertNull(s.latestModified)
        assertTrue(s.origins.isEmpty())
    }

    @Test fun sourceKinds() {
        fun k(vararg o: String) = RecordTypeSummary(o.size, null, o.toSet()).sourceKind(COROS_PACKAGE)
        assertEquals(SourceKind.Coros, k("x", COROS_PACKAGE))
        assertEquals(SourceKind.Other, k("x"))
        assertEquals(SourceKind.None, k())
    }

    @Test fun inspectorUsesReaderAndSurvivesErrors() = runTest {
        val good = RecordTypeInfo("g", "Good")
        val bad = RecordTypeInfo("b", "Bad")
        val reader = object : HealthRecordReader {
            override val types = listOf(good, bad)
            override suspend fun grantedPermissions() = emptySet<String>()
            override suspend fun readMeta(type: RecordTypeInfo, from: Instant, to: Instant): List<RecordMeta> {
                if (type == bad) throw SecurityException("no permission")
                assertEquals(30L * 86_400, to.epochSecond - from.epochSecond)
                return listOf(RecordMeta(t0, COROS_PACKAGE))
            }
        }
        val rows = HealthInspector(reader).inspect(t0.plusSeconds(100))
        assertEquals(1, rows[0].summary?.count)
        assertTrue(rows[1].error)
        assertNull(rows[1].summary)
    }
}
