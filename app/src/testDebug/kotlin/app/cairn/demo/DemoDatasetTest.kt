package app.cairn.demo

import app.cairn.core.model.Derived
import app.cairn.core.model.SleepRating
import app.cairn.core.model.WeeklyLoad
import app.cairn.core.model.WeeklyMileage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

private fun assertEquals(expected: Any?, actual: Any?, msg: String = "") = org.junit.Assert.assertEquals(msg, expected, actual)
private fun assertTrue(c: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, c)

class DemoDatasetTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-15T09:00:00Z")
    private val days = listOf(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 12, 3))

    @Test fun headlineNumbers() {
        val t = days[0]
        val d = DemoDataset.build(t, now, zone)
        val today = d.daily.first { it.date == t }
        assertEquals(9412, today.steps!!.value); assertEquals(640, today.calories!!.value); assertEquals(48, today.exerciseMinutes!!.value)
        assertEquals(54, d.vitals.first { it.date == t }.restingHr); assertEquals(28, d.vitals.first { it.date == t }.avgStress)
        assertEquals(55, d.vitals.mapNotNull { it.restingHr }.average().let { Math.round(it).toInt() })
        val sleep = d.sleep.first { it.date == t }
        assertEquals(82, sleep.score); assertEquals("Optimal", SleepRating.word(82)); assertEquals(432, sleep.asleepMinutes)
        assertEquals(sleep.asleepMinutes, sleep.deepMinutes!! + sleep.lightMinutes!! + sleep.remMinutes!!)
        assertEquals(sleep.inBedMinutes, sleep.asleepMinutes!! + sleep.awakeMinutes!!)
        assertEquals(sleep.inBedMinutes!! * 60L, sleep.end!!.epochSecond - sleep.start!!.epochSecond)
        val hrv = d.hrv.first { it.date == t }
        assertEquals(61, hrv.avgMs); assertTrue(hrv.avgMs!! in hrv.normalLowMs!!..hrv.normalHighMs!!)
        assertEquals(76, d.recovery.percent); assertEquals(5, d.recovery.hoursToFullRecovery)
        val l = d.load.maxByOrNull { it.date }!!
        assertEquals(31, l.shortTermLoad); assertEquals(48, l.longTermLoad); assertEquals(64, Derived.intensityTrendPercent(l.loadRatio!!)); assertEquals("Productive", l.comment)
        assertEquals(50, d.fitness.vo2max); assertEquals(68.0, d.fitness.runningLevel); assertEquals(320, d.fitness.thresholdPaceSecPerKm); assertEquals(15700, d.fitness.predictionMarathonSec)
        assertTrue(d.fitness.prediction5kSec!! < d.fitness.prediction10kSec!! && d.fitness.prediction10kSec!! < d.fitness.predictionHalfSec!! && d.fitness.predictionHalfSec!! < d.fitness.predictionMarathonSec!!)
    }

    @Test fun weeksAndWeeklyLoadAreConsistentOnAnyDate() {
        for (t in days) {
            val d = DemoDataset.build(t, now, zone)
            val prev = t.minusMonths(1)
            // The Activities list keeps its twelve runs last month (plus last week's two when last Monday fell in that month).
            assertTrue(d.activities.count { it.date.month == prev.month && it.date.year == prev.year } >= 12, "last month on $t")
            assertTrue(d.activities.count { it.date.month == t.month && it.date.year == t.year } >= 1, "this month on $t")
            val w = WeeklyLoad.compute(d.activities, d.details.associateBy { it.id }, t)
            assertEquals(DemoDataset.WEEK_LOAD, w.total, "weekly load on $t"); assertEquals(0, w.missing)
            assertEquals(d.activities.size, d.activities.map { it.id }.toSet().size)
            assertEquals(d.activities.map { it.id }.toSet(), d.details.map { it.id }.toSet())
            // Weekly mileage: Monday to Sunday, three runs this week and two last week, on every date (also across a month end).
            val m = WeeklyMileage.compute(d.activities, t)
            assertEquals(3, m.count, "this week on $t"); assertEquals(DemoDataset.WEEK_KM * 1000, m.totalMeters, "this week km on $t")
            assertEquals(2, m.lastWeekCount, "last week on $t"); assertEquals(DemoDataset.LAST_WEEK_KM * 1000, m.lastWeekMeters, "last week km on $t")
            assertEquals(14, m.changePercent)
        }
    }

    @Test fun sevenDaysOfHrvHeartRateAndStress() {
        val t = days[0]
        val d = DemoDataset.build(t, now, zone)
        assertEquals(7, d.hrv.size); assertEquals(7, d.vitals.size)
        assertEquals(setOf("Normal", "Elevated", "Low"), d.hrv.mapNotNull { it.assessment }.toSet())
        d.hrv.forEach {
            val word = if (it.avgMs!! > it.normalHighMs!!) "Elevated" else if (it.avgMs!! < it.normalLowMs!!) "Low" else "Normal"
            assertEquals(word, it.assessment, "assessment on ${it.date}")
        }
        assertTrue(d.vitals.all { it.restingHr != null && it.avgHr != null && it.minHr != null && it.maxHr != null && it.avgStress != null })
        assertTrue(d.vitals.map { it.avgHr }.toSet().size > 3 && d.vitals.map { it.avgStress }.toSet().size > 3)
        assertTrue(d.vitals.all { it.minHr!! < it.restingHr!! && it.restingHr!! < it.avgHr!! && it.avgHr!! < it.maxHr!! })
        val bands = d.vitals.map { app.cairn.core.model.StressBand.of(it.avgStress!!) }.toSet()
        assertTrue(bands.size >= 3, "stress bands $bands")
    }

    @Test fun neverContainsRealLookingIdentifiers() {
        val d = DemoDataset.build(days[0], now, zone)
        d.activities.forEach {
            assertTrue(it.id.startsWith(DemoDataset.ID_PREFIX), it.id)
            assertTrue(it.location in DemoDataset.PLACES, it.location.orEmpty())
            assertTrue(!Regex("[0-9]{9,}|@|[0-9]+\\.[0-9]+,\\s*-?[0-9]+\\.[0-9]+").containsMatchIn(it.id + it.location.orEmpty()), "id/location looks real")
        }
    }
}
