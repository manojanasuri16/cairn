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

    private fun run(id: String, d: String, km: Double?) = Activity(id, st, LocalDate.parse(d), "Run", distanceMeters = km?.times(1000))

    @Test fun stressBandsAreRelaxedLowMediumHigh() {
        assertEquals(null, StressBand.of(0))
        assertEquals(StressBand.Relaxed, StressBand.of(1)); assertEquals(StressBand.Relaxed, StressBand.of(25))
        assertEquals(StressBand.Low, StressBand.of(26)); assertEquals(StressBand.Low, StressBand.of(30)); assertEquals(StressBand.Low, StressBand.of(50))
        assertEquals(StressBand.Medium, StressBand.of(51)); assertEquals(StressBand.Medium, StressBand.of(75))
        assertEquals(StressBand.High, StressBand.of(76)); assertEquals(StressBand.High, StressBand.of(99)); assertEquals(StressBand.High, StressBand.of(120))
    }

    @Test fun weekRunsMondayToSundayAndSpansTwoMonths() {
        // Thursday 2030-10-31: this week is Mon 28 Oct to Sun 3 Nov (two months); last week Mon 21 to Sun 27 Oct.
        val today = LocalDate.parse("2030-10-31")
        assertEquals(LocalDate.parse("2030-10-28"), WeeklyMileage.mondayOf(today))
        assertEquals(LocalDate.parse("2030-10-28"), WeeklyMileage.mondayOf(LocalDate.parse("2030-11-03"))) // Sunday
        val acts = listOf(
            run("a", "2030-10-28", 5.0), run("b", "2030-10-31", 10.0), run("c", "2030-10-27", 8.0), run("d", "2030-10-21", 4.0),
            run("e", "2030-10-20", 50.0), run("f", "2030-11-01", 9.0), run("g", "2030-10-30", null),
        )
        val w = WeeklyMileage.compute(acts, today)
        assertEquals(15000.0, w.totalMeters, 0.001)
        assertEquals(3, w.count) // a, b and the one with no distance still counts as an activity
        assertEquals(listOf(5000.0, 0.0, 0.0, 10000.0, 0.0, 0.0, 0.0), w.dayMeters)
        assertEquals(12000.0, w.lastWeekMeters, 0.001)
        assertEquals(2, w.lastWeekCount)
        assertEquals(25, w.changePercent) // 15 against 12
    }

    @Test fun changeIsNullWithoutALastWeekAndNegativeWhenDown() {
        val today = LocalDate.parse("2030-11-06")
        assertNull(WeeklyMileage.compute(listOf(run("a", "2030-11-05", 3.0)), today).changePercent)
        assertEquals(-50, WeeklyMileage.compute(listOf(run("a", "2030-11-05", 3.0), run("b", "2030-10-30", 6.0)), today).changePercent)
        val sundayWeek = WeeklyMileage.compute(listOf(run("a", "2030-11-10", 3.0)), LocalDate.parse("2030-11-10"))
        assertEquals(3000.0, sundayWeek.dayMeters[6], 0.001) // Sunday is the last slot of its own week
    }
}
