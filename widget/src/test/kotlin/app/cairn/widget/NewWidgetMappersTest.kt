package app.cairn.widget

import app.cairn.core.model.Activity
import app.cairn.core.model.DataSource
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.Freshness
import app.cairn.core.model.HrvDay
import app.cairn.core.model.Stamp
import app.cairn.core.model.StressBand
import app.cairn.core.model.VitalsDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mappers of the HRV, Heart rate, Stress and Weekly mileage widgets: COROS wording, formats, bands, weeks and empty states. */
class NewWidgetMappersTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15") // Friday
    private val us = Locale.US
    private val st = Stamp(DataSource.COROS_MCP, now.minus(Duration.ofHours(2)))

    private fun <T> content(ui: WidgetUi<T>): WidgetUi.Content<T> = ui as WidgetUi.Content<T>
    private fun hrv(daysBack: Long, ms: Int?, word: String? = "Normal", lo: Int? = 46, hi: Int? = 58, base: Int? = 52) =
        HrvDay(today.minusDays(daysBack), st, ms, word, lo, hi, base)
    private fun vit(daysBack: Long, resting: Int? = null, avg: Int? = null, min: Int? = null, max: Int? = null, stress: Int? = null) =
        VitalsDay(today.minusDays(daysBack), st, avg, min, max, resting, stress)
    private fun run(id: String, d: String, km: Double?) = Activity(id, st, LocalDate.parse(d), "Run", distanceMeters = km?.times(1000))

    // ---- HRV ----

    @Test fun hrvShowsTheServersWordRangeAndBaseline() {
        val c = content(WidgetMapper.hrv(listOf(hrv(0, 65, "Elevated")), AuthState.Ok, now, today, us))
        assertEquals("65", c.body.number); assertEquals("ms", c.body.unit); assertEquals("65 ms", c.body.hero)
        assertEquals("Elevated", c.body.assessment)
        assertEquals("Normal range 46-58 ms", c.body.rangeText)
        assertEquals("Baseline 52 ms", c.body.baselineText)
        assertEquals(Tone.Cool, c.body.tone)
        assertEquals(7, c.body.nights.size)
        assertTrue(c.body.nights.last().isToday); assertEquals(65, c.body.nights.last().ms); assertNull(c.body.nights.first().ms)
        assertTrue(c.description, c.description.contains("65 milliseconds") && c.description.contains("Elevated") && c.description.contains("Updated 2h ago"))
        assertEquals(Freshness.Fresh, c.footer.tier)
    }

    @Test fun hrvToneFollowsTheWordNeverTheNumber() {
        assertEquals(Tone.Good, WidgetMapper.hrvTone("Normal"))
        assertEquals(Tone.Cool, WidgetMapper.hrvTone("Elevated"))
        assertEquals(Tone.Cool, WidgetMapper.hrvTone("Above normal"))
        assertEquals(Tone.Warn, WidgetMapper.hrvTone("Low"))
        assertEquals(Tone.Warn, WidgetMapper.hrvTone("Below normal"))
        assertEquals(Tone.Bad, WidgetMapper.hrvTone("Poor"))
        assertEquals(Tone.Neutral, WidgetMapper.hrvTone("Something new"))
    }

    @Test fun hrvDerivesAWordOnlyWhenTheServerGaveNone() {
        assertEquals("Low", content(WidgetMapper.hrv(listOf(hrv(0, 40, null)), AuthState.Ok, now, today)).body.assessment)
        assertEquals("Elevated", content(WidgetMapper.hrv(listOf(hrv(0, 70, " ")), AuthState.Ok, now, today)).body.assessment)
        assertEquals("Normal", content(WidgetMapper.hrv(listOf(hrv(0, 50, null)), AuthState.Ok, now, today)).body.assessment)
        val none = content(WidgetMapper.hrv(listOf(hrv(0, 50, null, null, null, null)), AuthState.Ok, now, today)).body
        assertEquals("No assessment", none.assessment); assertNull(none.rangeText); assertNull(none.lowMs)
    }

    @Test fun hrvUsesYesterdaysNightButNotOlderAndEmptyStatesHaveWords() {
        assertEquals("65", content(WidgetMapper.hrv(listOf(hrv(1, 65)), AuthState.Ok, now, today)).body.number)
        val old = WidgetMapper.hrv(listOf(hrv(3, 65)), AuthState.Ok, now, today)
        assertEquals(MessageKind.Empty, (old as WidgetUi.Message).kind); assertEquals("No HRV data", old.title)
        assertEquals(MessageKind.SignedOut, (WidgetMapper.hrv(emptyList(), AuthState.SignedOut, now, today) as WidgetUi.Message).kind)
        assertEquals(MessageKind.NeedsSignIn, (WidgetMapper.hrv(emptyList(), AuthState.NeedsSignIn, now, today) as WidgetUi.Message).kind)
        assertTrue(WidgetMapper.hrv(listOf(hrv(0, null)), AuthState.Ok, now, today) is WidgetUi.Message)
    }

    @Test fun hrvAxisHoldsRangeMarkerAndBaseline() {
        val b = content(WidgetMapper.hrv(listOf(hrv(0, 65)), AuthState.Ok, now, today)).body
        val a = HrvAxis.bar(b)
        assertTrue(a.lo <= 46 && a.hi >= 65)
        assertTrue(a.fraction(46) < a.fraction(58) && a.fraction(58) < a.fraction(65) && a.fraction(65) <= 1f)
        val n = HrvAxis.nights(b)
        assertEquals(0, n.lo); assertTrue(n.hi > 65)
    }

    // ---- heart rate ----

    @Test fun restingHrIsTheHeroWithItsSevenDayAverageAndTheDailyFigures() {
        val days = listOf(vit(6, 55), vit(5, 54), vit(4, 52), vit(3, 53), vit(2, 52), vit(1, 53), vit(0, 51, avg = 70, min = 44, max = 154))
        val c = content(WidgetMapper.heartRate(days, AuthState.Ok, now, today, us))
        assertEquals("51", c.body.number); assertEquals("bpm", c.body.unit); assertEquals("Resting HR", c.body.label)
        assertEquals("51 bpm", c.body.hero)
        assertEquals("7-day avg 53 bpm", c.body.sevenDayText) // (55+54+52+53+52+53+51) / 7 = 52.86
        assertEquals("Daily average 70 bpm", c.body.dailyText)
        assertEquals("44-154 bpm", c.body.minMaxText)
        assertEquals(listOf(55, 54, 52, 53, 52, 53, 51), c.body.series)
        assertTrue(c.description.contains("Updated 2h ago"))
    }

    @Test fun heartRateWithoutRestingFallsBackToTheDailyAverageAndNeverCallsItResting() {
        val c = content(WidgetMapper.heartRate(listOf(vit(0, avg = 68, min = 48, max = 130)), AuthState.Ok, now, today, us))
        assertEquals("68", c.body.number); assertEquals("Daily average", c.body.label); assertNull(c.body.sevenDayText); assertNull(c.body.restingBpm)
        assertEquals("48-130 bpm", c.body.minMaxText)
    }

    @Test fun sevenDayAverageNeedsTwoRowsAndEmptyStatesHaveWords() {
        assertNull(content(WidgetMapper.heartRate(listOf(vit(0, 51)), AuthState.Ok, now, today)).body.sevenDayText)
        assertEquals("No heart rate data", (WidgetMapper.heartRate(listOf(vit(4, 51)), AuthState.Ok, now, today) as WidgetUi.Message).title)
        assertEquals(MessageKind.SignedOut, (WidgetMapper.heartRate(emptyList(), AuthState.SignedOut, now, today) as WidgetUi.Message).kind)
        // min or max alone is not a range.
        assertNull(content(WidgetMapper.heartRate(listOf(vit(0, 51, avg = 70, min = 44)), AuthState.Ok, now, today)).body.minMaxText)
    }

    @Test fun sparklineFractionsSpanTheSeries() {
        assertEquals(listOf(0f, 0.5f, 1f), sparkFractions(listOf(50, 55, 60)))
        assertEquals(listOf(0.5f, 0.5f), sparkFractions(listOf(52, 52)))
        assertTrue(sparkFractions(emptyList()).isEmpty())
        val axis = heartAxis(44, 154)
        assertTrue(axis.lo <= 34 && axis.hi >= 164 && axis.lo % 10 == 0 && axis.hi % 10 == 0)
    }

    // ---- stress ----

    @Test fun stressBandsAndTonesUseTheAssumedCorosWords() {
        val expect = mapOf(1 to "Relaxed", 25 to "Relaxed", 26 to "Low", 30 to "Low", 50 to "Low", 51 to "Medium", 75 to "Medium", 76 to "High", 99 to "High")
        for ((v, word) in expect) {
            val c = content(WidgetMapper.stress(listOf(vit(0, stress = v)), AuthState.Ok, now, today, us))
            assertEquals("$v", word, c.body.level); assertEquals("$v", v.toString(), c.body.number)
        }
        assertEquals(Tone.Cool, WidgetMapper.stressTone(StressBand.Relaxed)); assertEquals(Tone.Good, WidgetMapper.stressTone(StressBand.Low))
        assertEquals(Tone.Warn, WidgetMapper.stressTone(StressBand.Medium)); assertEquals(Tone.Bad, WidgetMapper.stressTone(StressBand.High))
    }

    @Test fun stressBarsCoverSevenDaysColouredByBand() {
        val days = listOf(vit(5, stress = 20), vit(3, stress = 60), vit(1, stress = 80), vit(0, stress = 30))
        val c = content(WidgetMapper.stress(days, AuthState.Ok, now, today, us))
        assertEquals(7, c.body.bars.size)
        assertEquals(listOf(null, 20, null, 60, null, 80, 30), c.body.bars.map { it.value })
        assertEquals(listOf(Tone.Neutral, Tone.Cool, Tone.Neutral, Tone.Warn, Tone.Neutral, Tone.Bad, Tone.Good), c.body.bars.map { it.tone })
        assertEquals(1f, c.body.bars[5].fraction, 0.001f) // the tallest bar
        assertTrue(c.body.bars.last().isToday)
        assertEquals("7-day avg 48", c.body.sevenDayText) // (20+60+80+30) / 4 = 47.5
        assertTrue(c.description.contains("Low") && c.description.contains("Updated 2h ago"))
    }

    @Test fun stressEmptyStates() {
        assertEquals("No stress data", (WidgetMapper.stress(listOf(vit(0, stress = 0)), AuthState.Ok, now, today) as WidgetUi.Message).title)
        assertEquals("No stress data", (WidgetMapper.stress(listOf(vit(3, stress = 30)), AuthState.Ok, now, today) as WidgetUi.Message).title)
        assertEquals(MessageKind.NeedsSignIn, (WidgetMapper.stress(emptyList(), AuthState.NeedsSignIn, now, today) as WidgetUi.Message).kind)
        assertNull(content(WidgetMapper.stress(listOf(vit(0, stress = 30)), AuthState.Ok, now, today)).body.sevenDayText)
    }

    // ---- weekly mileage ----

    @Test fun weeklyMileageUsesCorosFormatsAndCountsEveryActivity() {
        val acts = listOf(run("a", "2030-03-11", 8.1), run("b", "2030-03-13", 10.0), run("c", "2030-03-15", 7.0), run("w", "2030-03-14", null),
            run("l1", "2030-03-05", 12.0), run("l2", "2030-03-08", 10.0))
        val c = content(WidgetMapper.weeklyMileage(acts, AuthState.Ok, now, today, us))
        assertEquals("25.10", c.body.number); assertEquals("km", c.body.unit); assertEquals("25.10 km", c.body.hero)
        assertEquals("4 activities", c.body.countText)
        assertEquals("+14% vs last week", c.body.comparison) // 25.1 against 22
        assertEquals(14, c.body.changePercent)
        assertEquals(listOf(8100.0, 0.0, 10000.0, 0.0, 7000.0, 0.0, 0.0), c.body.bars.map { it.meters })
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), c.body.bars.map { it.letter })
        assertTrue(c.body.bars[4].isToday)
        assertEquals(5, c.body.cumulative.size); assertEquals(1f, c.body.cumulative.last(), 0.001f)
        assertTrue(c.description.contains("25.10 kilometres") || c.description.contains("25.10 km"))
        assertTrue(c.description.contains("Updated 2h ago"))
    }

    @Test fun milesHonourTheUnitsOverrideWithTwoDecimals() {
        val c = content(WidgetMapper.weeklyMileage(listOf(run("a", "2030-03-12", 16.09344)), AuthState.Ok, now, today, us, DisplayPrefs(DistanceUnit.Mi)))
        assertEquals("10.00", c.body.number); assertEquals("mi", c.body.unit)
    }

    @Test fun aWeekThatSpansTwoMonthsIsOneWeek() {
        val sunday = LocalDate.parse("2030-11-03") // Mon 28 Oct to Sun 3 Nov
        val acts = listOf(run("a", "2030-10-28", 5.0), run("b", "2030-10-31", 4.0), run("c", "2030-11-02", 6.0), run("d", "2030-10-27", 20.0))
        val c = content(WidgetMapper.weeklyMileage(acts, AuthState.Ok, now, sunday, us))
        assertEquals("15.00", c.body.number); assertEquals("3 activities", c.body.countText)
        assertEquals("-25% vs last week", c.body.comparison)
        assertTrue(c.body.bars[6].isToday) // Sunday is the last slot
    }

    @Test fun lastWeekComparisonOnlyWhenLastWeekHasDistance() {
        assertNull(content(WidgetMapper.weeklyMileage(listOf(run("a", "2030-03-12", 5.0)), AuthState.Ok, now, today)).body.comparison)
        assertEquals("Same as last week", content(WidgetMapper.weeklyMileage(listOf(run("a", "2030-03-12", 5.0), run("b", "2030-03-05", 5.0)), AuthState.Ok, now, today)).body.comparison)
        val quiet = content(WidgetMapper.weeklyMileage(listOf(run("b", "2030-03-05", 5.0)), AuthState.Ok, now, today)).body
        assertEquals("0.00", quiet.number); assertEquals("0 activities", quiet.countText); assertEquals("-100% vs last week", quiet.comparison)
        assertEquals("1 activity", content(WidgetMapper.weeklyMileage(listOf(run("a", "2030-03-12", 5.0)), AuthState.Ok, now, today)).body.countText)
    }

    @Test fun weeklyMileageEmptyStates() {
        assertEquals("No activities yet", (WidgetMapper.weeklyMileage(emptyList(), AuthState.Ok, now, today) as WidgetUi.Message).title)
        assertEquals(MessageKind.SignedOut, (WidgetMapper.weeklyMileage(emptyList(), AuthState.SignedOut, now, today) as WidgetUi.Message).kind)
        // Activities from long ago are not this week or last week.
        assertTrue(WidgetMapper.weeklyMileage(listOf(run("o", "2030-01-02", 9.0)), AuthState.Ok, now, today) is WidgetUi.Message)
    }

    // ---- overview additions ----

    @Test fun overviewListsHeartRateHrvStressAndWeeklyMileageAndKeepsOldIds() {
        val names = OverviewId.entries.map { it.name }
        for (n in listOf("HeartRate", "Hrv", "Stress", "WeeklyMileage")) assertTrue(n, n in names)
        // Existing stats keep their order, so a stored choice keeps meaning the same thing.
        assertEquals(listOf("Steps", "Sleep", "SleepScore", "Recovery", "RestingHr", "Calories", "Stress", "LoadRatio", "Vo2", "LastDistance", "HeartRate", "Hrv", "WeeklyLoad", "TodayRun"), names.take(14))
        val w = app.cairn.core.model.WeeklyMileage.compute(listOf(run("a", "2030-03-12", 5.0)), today)
        val x = WidgetMapper.overviewExtras(null, null, null, null, null, now, today, mileage = w, mileageStamp = now)
        val item = x.items.single { it.id == OverviewId.WeeklyMileage }
        assertEquals("5.00", item.number); assertEquals("km", item.unit)
        assertNotNull(x.footer)
        assertFalse(WidgetMapper.overviewExtras(null, null, null, null, null, now, today, mileage = app.cairn.core.model.WeeklyMileage.compute(emptyList(), today), mileageStamp = now).items.any { it.id == OverviewId.WeeklyMileage })
    }
}
