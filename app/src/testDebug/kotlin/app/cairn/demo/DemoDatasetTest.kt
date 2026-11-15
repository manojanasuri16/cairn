package app.cairn.demo

import app.cairn.core.model.Derived
import app.cairn.core.model.SleepRating
import app.cairn.core.model.WeeklyLoad
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

    @Test fun monthsAndWeeklyLoadAreConsistentOnAnyDate() {
        for (t in days) {
            val d = DemoDataset.build(t, now, zone)
            assertEquals(3, d.activities.count { it.date.month == t.month && it.date.year == t.year }, "this month on $t")
            val prev = t.minusMonths(1)
            assertEquals(12, d.activities.count { it.date.month == prev.month && it.date.year == prev.year }, "last month on $t")
            val w = WeeklyLoad.compute(d.activities, d.details.associateBy { it.id }, t)
            assertEquals(DemoDataset.WEEK_LOAD, w.total, "weekly load on $t"); assertEquals(0, w.missing)
            assertEquals(d.activities.size, d.activities.map { it.id }.toSet().size)
            assertEquals(d.activities.map { it.id }.toSet(), d.details.map { it.id }.toSet())
        }
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
