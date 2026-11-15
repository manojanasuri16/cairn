package app.cairn.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DerivedTest {
    private val st = Stamp(DataSource.COROS_MCP, Instant.parse("2030-01-09T10:00:00Z"))
    private fun act(id: String, d: String) = Activity(id, st, LocalDate.parse(d), "Run")
    private fun det(id: String, load: Int?) = ActivityDetail(id, st, trainingLoad = load)

    @Test fun intensityTrendIsRatioAsPercent() {
        assertEquals(47, Derived.intensityTrendPercent(0.47))
        assertEquals(10, Derived.intensityTrendPercent(0.10))
        assertEquals(121, Derived.intensityTrendPercent(1.21))
        assertEquals(0, Derived.intensityTrendPercent(0.004))
    }

    @Test fun sevenDayLoadSumsOnlyTheWindow() {
        val today = LocalDate.parse("2030-01-09") // Wednesday
        val acts = listOf(act("a", "2030-01-09"), act("b", "2030-01-03"), act("c", "2030-01-02"), act("d", "2030-01-08"))
        val details = mapOf("a" to det("a", 100), "b" to det("b", 12), "c" to det("c", 500), "d" to det("d", null))
        val w = WeeklyLoad.compute(acts, details, today)
        assertEquals(112, w.total) // c is 7 days back: outside the window
        assertEquals(1, w.missing)
        assertEquals(3, w.activityCount)
    }

    @Test fun weekBarsAreMondayFirstAndEndAtToday() {
        val today = LocalDate.parse("2030-01-09") // Wednesday
        val acts = listOf(act("a", "2030-01-07"), act("b", "2030-01-09"), act("c", "2030-01-09"))
        val w = WeeklyLoad.compute(acts, mapOf("a" to det("a", 30), "b" to det("b", 20), "c" to det("c", 5)), today)
        assertEquals(listOf(30, 0, 25, null, null, null, null), w.weekBars)
    }

    @Test fun noActivitiesGivesZero() {
        val w = WeeklyLoad.compute(emptyList(), emptyMap(), LocalDate.parse("2030-01-09"))
        assertEquals(0, w.total)
        assertNull(w.weekBars[6])
    }

    @Test fun sleepRatingBands() {
        assertEquals("Optimal", SleepRating.word(87)); assertEquals("Optimal", SleepRating.word(80))
        assertEquals("Good", SleepRating.word(79)); assertEquals("Good", SleepRating.word(60))
        assertEquals("Fair", SleepRating.word(59)); assertEquals("Fair", SleepRating.word(40))
        assertEquals("Poor", SleepRating.word(39))
    }
}
