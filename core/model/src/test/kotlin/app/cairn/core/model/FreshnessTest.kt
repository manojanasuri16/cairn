package app.cairn.core.model

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessTest {
    @Test fun freshBelowThreshold() = assertEquals(Freshness.Fresh, Freshness.fromAge(Duration.ofMinutes(179)))
    @Test fun agingAtFreshLimit() = assertEquals(Freshness.Aging, Freshness.fromAge(Duration.ofHours(3)))
    @Test fun agingBelowStaleLimit() = assertEquals(Freshness.Aging, Freshness.fromAge(Duration.ofMinutes(719)))
    @Test fun staleAtAgingLimit() = assertEquals(Freshness.Stale, Freshness.fromAge(Duration.ofHours(12)))
    @Test fun negativeAgeIsFresh() = assertEquals(Freshness.Fresh, Freshness.fromAge(Duration.ofMinutes(-5)))
    @Test fun customThresholds() =
        assertEquals(Freshness.Stale, Freshness.fromAge(Duration.ofMinutes(31), Duration.ofMinutes(10), Duration.ofMinutes(30)))
}
