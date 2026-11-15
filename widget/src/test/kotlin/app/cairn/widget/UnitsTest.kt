package app.cairn.widget

import app.cairn.core.model.Activity
import app.cairn.core.model.DataSource
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.Freshness
import app.cairn.core.model.Stamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")

    @Test fun milesFormatting() {
        assertEquals("5.0 mi", Fmt.distance(8046.72, DistanceUnit.Mi, Locale.US))
        assertEquals("7.2 km", Fmt.distance(7200.0, DistanceUnit.Km, Locale.US))
        assertEquals("8:24 /mi", Fmt.pace(313, DistanceUnit.Mi))
        assertEquals("5:13 /km", Fmt.pace(313))
        assertEquals("1:52:03", Fmt.raceTime(6723))
        assertEquals("24:10", Fmt.raceTime(1450))
    }

    @Test fun lastActivityRespectsUnit() {
        val a = Activity("x", Stamp(DataSource.COROS_MCP, now.minusSeconds(60)), today, "run", distanceMeters = 8046.72, avgPaceSecPerKm = 313)
        val ui = WidgetMapper.lastActivity(a, AuthState.Ok, now, today, Locale.US, DisplayPrefs(DistanceUnit.Mi)) as WidgetUi.Content
        assertEquals("5.0 mi", ui.body.distance)
        assertEquals("8:24 /mi", ui.body.pace)
    }

    @Test fun footerUsesConfiguredThresholds() {
        val at = listOf(now.minus(Duration.ofHours(2)))
        assertEquals(Freshness.Fresh, WidgetMapper.footer(at, now).tier)
        assertEquals(Freshness.Aging, WidgetMapper.footer(at, now, DisplayPrefs(freshHours = 1)).tier)
        assertEquals(Freshness.Stale, WidgetMapper.footer(at, now, DisplayPrefs(freshHours = 1, agingHours = 1)).tier)
    }
}
