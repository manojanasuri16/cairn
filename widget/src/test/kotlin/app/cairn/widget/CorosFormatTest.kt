package app.cairn.widget

import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.DataSource
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.HrvDay
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.WeeklyLoad
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The COROS spelling of numbers: pace, durations, distance, percent, dates. */
class CorosFormatTest {
    private val us = Locale.US
    private val now = Instant.parse("2030-01-09T12:00:00Z")
    private val today = LocalDate.parse("2030-01-09")
    private val st = Stamp(DataSource.COROS_MCP, now.minusSeconds(600))

    @Test fun paceUsesApostropheAndDoubleQuote() {
        assertEquals("9'50\" /km", Fmt.pace(590))
        assertEquals("4'05\" /km", Fmt.pace(245))
        assertEquals("6'07\" /mi", Fmt.pace(228, DistanceUnit.Mi))
    }

    @Test fun durations() {
        assertEquals("54:37", Fmt.clock(54 * 60 + 37))
        assertEquals("1:02:03", Fmt.clock(3723))
        assertEquals("6 h 47 min", Fmt.duration(407))
        assertEquals("47 min", Fmt.duration(47))
        assertEquals("6 h", Fmt.duration(360))
    }

    @Test fun distanceHasTwoDecimals() {
        assertEquals("5.55 km", Fmt.distance(5550.0, DistanceUnit.Km, us))
        assertEquals("5.50 km", Fmt.distance(5500.0, DistanceUnit.Km, us))
        assertEquals("3.45 mi", Fmt.distance(5550.0, DistanceUnit.Mi, us))
    }

    @Test fun percentAndIntensityTrend() {
        assertEquals("99 %", Fmt.percent(99))
        assertEquals("47%", Fmt.intensityTrend(0.47))
        assertEquals("112%", Fmt.intensityTrend(1.12))
    }

    @Test fun runningLevelKeepsOneDecimalOnlyWhenPresent() {
        assertEquals("73.3", Fmt.runningLevel(73.3, us))
        assertEquals("73", Fmt.runningLevel(73.0, us))
    }

    @Test fun datesAndRecoveryWording() {
        assertEquals("Sunday, 15 Nov", Fmt.dateLong(LocalDate.parse("2026-11-15"), us))
        assertEquals("Sunday", Fmt.weekday(LocalDate.parse("2026-11-15"), us))
        assertEquals("3 hrs till full recovery", Fmt.timeToFull(3))
        assertEquals("1 hr till full recovery", Fmt.timeToFull(1))
    }

    @Test fun intensityTrendShownInTrainingLoadWidget() {
        val d = app.cairn.core.model.TrainingLoadDay(today, st, 20, 42, 0.47, "Decreasing")
        val c = WidgetMapper.trainingLoad(listOf(d), null, null, AuthState.Ok, now, today, us) as WidgetUi.Content
        assertEquals("47%", c.body.ratioText); assertEquals("Decreasing", c.body.comment)
        assertTrue(c.description.contains("Intensity Trend 47 percent")); assertTrue(!c.description.contains("Load ratio"))
    }

    @Test fun todayUsesRestingHrFromTheDedicatedTool() {
        val daily = DailySummary(today, steps = SourcedValue(9000, DataSource.COROS_MCP, st.sourceUpdatedAt), restingHr = SourcedValue(52, DataSource.COROS_MCP, st.sourceUpdatedAt))
        val v = VitalsDay(today, st, restingHr = 51, avgStress = 30)
        val c = WidgetMapper.today(daily, null, null, AuthState.Ok, now, today, vitals = v) as WidgetUi.Content
        assertEquals("51 bpm", c.body.metrics.first { it.id == MetricId.RestingHr }.value)
        assertEquals("30", c.body.metrics.first { it.id == MetricId.Stress }.value)
    }

    @Test fun overviewExtrasAndStatList() {
        val vitals = VitalsDay(today, st, avgHr = 70)
        val hrv = HrvDay(today, st, avgMs = 65, assessment = "Above normal")
        val run = Activity("L1", st, today, "Run", distanceMeters = 5550.0, sportType = 100)
        val week = WeeklyLoad.compute(listOf(run), mapOf("L1" to ActivityDetail("L1", st, 112)), today)
        val x = WidgetMapper.overviewExtras(vitals, hrv, week, st.sourceUpdatedAt, run, now, today, locale = us)
        assertEquals(listOf(OverviewId.HeartRate, OverviewId.Hrv, OverviewId.WeeklyLoad, OverviewId.TodayRun), x.items.map { it.id })
        assertEquals("70" to "bpm", x.items[0].number to x.items[0].unit)
        assertEquals("112", x.items[2].number)
        assertEquals("5.55" to "km", x.items[3].number to x.items[3].unit)
        assertNotNull(x.footer)
        // Old ids stay (placed widgets store them by name) and the label changed to the COROS name.
        assertEquals("Intensity Trend", OverviewId.LoadRatio.label)
        assertEquals(OverviewId.LoadRatio, OverviewId.parse("LoadRatio"))
        assertNull(WidgetMapper.overviewExtras(null, null, null, null, null, now, today).footer)
    }

    @Test fun overviewShowsExtrasWithoutOtherSources() {
        val vitals = VitalsDay(today, st, avgHr = 70)
        val x = WidgetMapper.overviewExtras(vitals, null, null, null, null, now, today)
        val ui = OverviewMapper.build(WidgetUi.Loading, WidgetUi.Loading, WidgetUi.Loading, WidgetUi.Loading, WidgetUi.Loading, WidgetUi.Loading, x) as WidgetUi.Content
        assertEquals("70", ui.body.find(OverviewId.HeartRate)!!.number)
    }
}
