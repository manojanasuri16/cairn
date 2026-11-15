package app.cairn.core.common

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeAgeTest {
    @Test fun justNow() = assertEquals("Updated just now", RelativeAge.format(Duration.ofSeconds(59)))
    @Test fun futureClampsToJustNow() = assertEquals("Updated just now", RelativeAge.format(Duration.ofMinutes(-3)))
    @Test fun minutes() = assertEquals("Updated 5m ago", RelativeAge.format(Duration.ofMinutes(5)))
    @Test fun hours() = assertEquals("Updated 2h ago", RelativeAge.format(Duration.ofMinutes(150)))
    @Test fun days() = assertEquals("Updated 3d ago", RelativeAge.format(Duration.ofHours(72)))
    @Test fun boundaryHour() = assertEquals("Updated 1h ago", RelativeAge.format(Duration.ofMinutes(60)))
    @Test fun instants() {
        val now = Instant.parse("2026-11-15T12:00:00Z")
        assertEquals("Updated 2h ago", RelativeAge.format(now.minusSeconds(2 * 3600 + 10), now))
    }
}
