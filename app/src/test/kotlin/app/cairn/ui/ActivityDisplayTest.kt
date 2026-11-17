package app.cairn.ui

import app.cairn.core.model.Activity
import app.cairn.core.model.DataSource
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.Stamp
import app.cairn.widget.AuthState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDisplayTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")
    private fun stamp() = Stamp(DataSource.COROS_MCP, now.minus(Duration.ofHours(1)))
    private val strength = Activity("s1", stamp(), today, "Strength", start = Instant.parse("2030-03-15T08:00:00Z"), durationSec = 6805, avgHr = 121, calories = 312, location = "Morning workout", sportType = 402)
    private val run = Activity("r1", stamp(), today, "Run", durationSec = 3277, distanceMeters = 5550.0, avgPaceSecPerKm = 590, avgHr = 150, calories = 400, sportType = 100)

    @Test fun sportMapping() {
        val cases = mapOf(
            "Run" to SportKind.Run, "Trail_Run" to SportKind.Run, "Indoor Run" to SportKind.Run, "Treadmill" to SportKind.Run,
            "Hike" to SportKind.Walk, "Walk" to SportKind.Walk, "Outdoor Cycling" to SportKind.Cycle, "Indoor Bike" to SportKind.Cycle,
            "Pool Swim" to SportKind.Swim, "Open Water" to SportKind.Swim, "Rowing" to SportKind.Water, "Kayak" to SportKind.Water,
            "Strength" to SportKind.Strength, "Gym" to SportKind.Strength, "Indoor Cardio" to SportKind.Cardio, "HIIT" to SportKind.Cardio,
            "Yoga" to SportKind.Yoga, "Pilates" to SportKind.Yoga, "Ski" to SportKind.Snow, "Snowboard" to SportKind.Snow,
            "Tennis" to SportKind.Ball, "Badminton" to SportKind.Ball, "Underwater basket weaving" to SportKind.Workout, "" to SportKind.Workout,
        )
        cases.forEach { (name, kind) -> assertEquals(name, kind, SportKind.of(name, null)) }
        assertEquals(SportKind.Run, SportKind.of("Activity", 100))
        assertEquals(SportKind.Strength, SportKind.of("Mystery", 405))
        assertEquals(SportKind.Workout, SportKind.of("Mystery", 999))
        assertEquals(SportKind.Workout, SportKind.of(null, null))
        SportKind.entries.forEach { assertTrue(it.label.length > 1) } // never a bare initial
    }

    @Test fun todayCardNoDistanceUsesDurationHeroAndHidesMissing() {
        val s = AppMapper.today(emptyList(), emptyList(), null, emptyList(), AuthState.Ok, now, today, DisplayPrefs(), Locale.US, ZoneOffset.UTC, TodayExtras(activities = listOf(strength)))
        val a = s.todayActivity!!
        assertEquals(SportKind.Strength, a.kind)
        assertEquals("Strength", a.sportLabel)
        assertNull(a.distance)
        assertEquals("1 h 53 min", a.hero)
        assertEquals(listOf("121 bpm", "312 kcal"), a.secondary) // no distance: no clock duration, no pace
    }

    @Test fun todayCardWithDistanceKeepsDistanceHero() {
        val s = AppMapper.today(emptyList(), emptyList(), null, emptyList(), AuthState.Ok, now, today, DisplayPrefs(), Locale.US, ZoneOffset.UTC, TodayExtras(activities = listOf(run)))
        val a = s.todayActivity!!
        assertEquals("5.55 km", a.hero)
        assertEquals(listOf("54:37", "9'50\" /km", "150 bpm", "400 kcal"), a.secondary)
    }

    @Test fun activityRowsHideEmptyMetrics() {
        val bare = strength.copy(id = "b", avgHr = null, calories = null)
        val st = AppMapper.activities(listOf(strength, bare), AuthState.Ok, now, today, DisplayPrefs(), Locale.US, ZoneOffset.UTC)
        assertEquals(SportKind.Strength, st.rows[0].kind)
        assertEquals("1 h 53 min", st.rows[0].hero)
        assertEquals(listOf("121 bpm", "312 kcal"), st.rows[0].secondary)
        assertTrue(st.rows[1].secondary.isEmpty())
    }
}
