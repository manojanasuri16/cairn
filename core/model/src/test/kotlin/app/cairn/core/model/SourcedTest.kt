package app.cairn.core.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcedTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private fun sv(v: Int, src: DataSource, ageH: Long) = SourcedValue(v, src, now.minus(Duration.ofHours(ageH)))

    @Test fun freshnessUsesSourceUpdatedAt() {
        assertEquals(Freshness.Fresh, sv(1, DataSource.COROS_MCP, 1).freshness(now))
        assertEquals(Freshness.Aging, sv(1, DataSource.COROS_MCP, 5).freshness(now))
        assertEquals(Freshness.Stale, sv(1, DataSource.HEALTH_CONNECT, 13).freshness(now))
    }

    @Test fun withFreshnessCarriesFields() {
        val f = sv(7, DataSource.HEALTH_CONNECT, 4).withFreshness(now)
        assertEquals(7, f.value)
        assertEquals(DataSource.HEALTH_CONNECT, f.source)
        assertEquals(Freshness.Aging, f.freshness)
    }

    @Test fun mapKeepsProvenance() {
        val m = sv(2, DataSource.COROS_MCP, 1).map { it * 10 }
        assertEquals(20, m.value)
        assertEquals(DataSource.COROS_MCP, m.source)
    }

    @Test fun newestPicksLatestAndBreaksTiesToPreferred() {
        val old = sv(1, DataSource.COROS_MCP, 5)
        val new = sv(2, DataSource.HEALTH_CONNECT, 1)
        assertEquals(2, newest(old, new)!!.value)
        assertEquals(2, newest(new, old)!!.value)
        val tieA = sv(1, DataSource.HEALTH_CONNECT, 2)
        val tieB = sv(2, DataSource.COROS_MCP, 2)
        assertEquals(2, newest(tieA, tieB)!!.value)
        assertEquals(2, newest(tieB, tieA)!!.value)
        assertEquals(1, newest(old, null)!!.value)
        assertEquals(2, newest(null, new)!!.value)
        assertNull(newest<Int>(null, null))
    }
}
