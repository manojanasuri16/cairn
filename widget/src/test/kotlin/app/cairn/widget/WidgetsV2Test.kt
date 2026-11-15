package app.cairn.widget

import app.cairn.core.model.DataSource
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Freshness
import app.cairn.core.model.SleepNight
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsV2Test {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")
    private val us = Locale.US
    private val utc = ZoneId.of("UTC")
    private fun stamp(h: Long = 1, src: DataSource = DataSource.COROS_MCP) = Stamp(src, now.minus(Duration.ofHours(h)))

    @Suppress("UNCHECKED_CAST")
    private fun <T> content(u: WidgetUi<T>): WidgetUi.Content<T> = u as WidgetUi.Content<T>

    // ---- Training load ----
    private fun load(daysAgo: Long, v: Int?, ratio: Double? = null, comment: String? = null) =
        TrainingLoadDay(today.minusDays(daysAgo), stamp(), shortTermLoad = v, loadRatio = ratio, comment = comment)

    @Test fun loadBarsAndRatio() {
        val days = listOf(load(6, 100), load(3, 50), load(1, 200), load(0, 150, 1.14, "OPTIMAL"))
        val c = content(WidgetMapper.trainingLoad(days, FitnessSnapshot(stamp(), vo2max = 52), 51, AuthState.Ok, now, today, us))
        assertEquals(7, c.body.bars.size)
        assertTrue(c.body.bars.last().isToday)
        assertEquals(1f, c.body.bars[5].fraction, 0.001f)
        assertEquals(0f, c.body.bars[1].fraction, 0f)
        assertEquals(0.5f, c.body.bars[0].fraction, 0.001f) // 100/200
        assertEquals("1.1", c.body.ratioText)
        assertEquals("Optimal", c.body.comment)
        assertEquals("VO2max 52", c.body.vo2Text)
        assertEquals("+1", c.body.vo2Trend)
        assertEquals(Screen.Fitness, c.screen)
        assertTrue(c.description.contains("Load ratio 1.1"))
    }

    @Test fun loadFallbackLabelAndNoTrend() {
        val c = content(WidgetMapper.trainingLoad(listOf(load(0, 80, 1.6)), null, null, AuthState.Ok, now, today, us))
        assertEquals("Very high", c.body.comment)
        assertNull(c.body.vo2Text)
        assertNull(c.body.vo2Trend)
        assertEquals("Low", WidgetMapper.ratioLabel(0.5))
        assertEquals("High", WidgetMapper.ratioLabel(1.4))
    }

    @Test fun loadIgnoresFutureDaysAndEmptyStates() {
        val m = WidgetMapper.trainingLoad(emptyList(), null, null, AuthState.Ok, now, today, us) as WidgetUi.Message
        assertEquals(MessageKind.Empty, m.kind)
        val future = WidgetMapper.trainingLoad(listOf(load(-2, 90)), null, null, AuthState.Ok, now, today, us)
        assertTrue(future is WidgetUi.Message)
        assertEquals(MessageKind.SignedOut, (WidgetMapper.trainingLoad(emptyList(), null, null, AuthState.SignedOut, now, today, us) as WidgetUi.Message).kind)
        assertEquals(MessageKind.NeedsSignIn, (WidgetMapper.trainingLoad(emptyList(), null, null, AuthState.NeedsSignIn, now, today, us) as WidgetUi.Message).kind)
    }

    @Test fun loadFooterTierStale() {
        val old = TrainingLoadDay(today, Stamp(DataSource.COROS_MCP, now.minus(Duration.ofHours(20))), shortTermLoad = 10)
        val c = content(WidgetMapper.trainingLoad(listOf(old), null, null, AuthState.Ok, now, today, us))
        assertEquals(Freshness.Stale, c.footer.tier)
        assertTrue(c.footer.opensCoros)
    }

    // ---- Sleep ----
    private fun night(
        asleep: Int? = 480, deep: Int? = 90, light: Int? = 270, rem: Int? = 100, awake: Int? = 20,
        src: DataSource = DataSource.COROS_MCP, date: LocalDate = today,
    ) = SleepNight(
        date, stamp(2, src), score = 84, asleepMinutes = asleep, deepMinutes = deep, lightMinutes = light, remMinutes = rem, awakeMinutes = awake,
        start = Instant.parse("2030-03-15T00:28:00Z"), end = Instant.parse("2030-03-15T08:30:00Z"),
    )

    @Test fun sleepContent() {
        val c = content(WidgetMapper.sleep(night(), AuthState.Ok, now, today, utc))
        assertEquals("8h", c.body.durationText)
        assertEquals("Score 84", c.body.scoreText)
        assertEquals("00:28–08:30", c.body.window)
        assertEquals(listOf(SleepStage.Deep, SleepStage.Light, SleepStage.Rem, SleepStage.Awake), c.body.stages!!.map { it.stage })
        assertNull(c.body.stageNote)
        assertTrue(c.description.contains("from 00:28 to 08:30"))
    }

    @Test fun sleepWindowUsesZone() {
        val c = content(WidgetMapper.sleep(night(), AuthState.Ok, now, today, ZoneId.of("Asia/Kolkata")))
        assertEquals("05:58–14:00", c.body.window)
    }

    @Test fun sleepApproximateStages() {
        // 90+270+100 = 460 vs 480 asleep: within 5% (24 min) -> exact
        assertFalse(WidgetMapper.stagesApproximate(night(), 480))
        // far off -> approximate
        val off = content(WidgetMapper.sleep(night(light = 100), AuthState.Ok, now, today, utc))
        assertEquals("Stage times are approximate", off.body.stageNote)
        // Health Connect always approximate
        val hc = content(WidgetMapper.sleep(night(src = DataSource.HEALTH_CONNECT), AuthState.Ok, now, today, utc))
        assertEquals("Stage times are approximate", hc.body.stageNote)
    }

    @Test fun sleepWithoutStagesOrWindow() {
        val n = night(deep = null, light = null, rem = null, awake = null).copy(start = null, end = null, score = null)
        val c = content(WidgetMapper.sleep(n, AuthState.Ok, now, today, utc))
        assertNull(c.body.stages)
        assertNull(c.body.window)
        assertNull(c.body.scoreText)
        assertNull(c.body.stageNote)
    }

    @Test fun sleepEmptyStates() {
        assertTrue(WidgetMapper.sleep(null, AuthState.Ok, now, today, utc) is WidgetUi.Message)
        assertTrue(WidgetMapper.sleep(night(asleep = null), AuthState.Ok, now, today, utc) is WidgetUi.Message)
        assertTrue(WidgetMapper.sleep(night(date = today.minusDays(3)), AuthState.Ok, now, today, utc) is WidgetUi.Message)
        assertEquals(MessageKind.SignedOut, (WidgetMapper.sleep(null, AuthState.SignedOut, now, today, utc) as WidgetUi.Message).kind)
    }

    // ---- Fitness ----
    private val fit = FitnessSnapshot(stamp(), vo2max = 52, thresholdPaceSecPerKm = 270, prediction5kSec = 1450, prediction10kSec = 3005, predictionHalfSec = 6612, predictionMarathonSec = 13900)

    @Test fun fitnessKm() {
        val c = content(WidgetMapper.fitness(fit, AuthState.Ok, now))
        assertEquals("52", c.body.vo2Text)
        assertEquals("4:30 /km", c.body.thresholdPace)
        assertEquals(listOf("5K" to "24:10", "10K" to "50:05", "HM" to "1:50:12", "M" to "3:51:40"), c.body.predictions.map { it.label to it.time })
        assertTrue(c.description.contains("half marathon 1:50:12"))
    }

    @Test fun fitnessMilesPace() {
        val c = content(WidgetMapper.fitness(fit, AuthState.Ok, now, DisplayPrefs(unit = DistanceUnit.Mi)))
        assertEquals("7:15 /mi", c.body.thresholdPace)
    }

    @Test fun fitnessPartialAndEmpty() {
        val c = content(WidgetMapper.fitness(FitnessSnapshot(stamp(), vo2max = 48), AuthState.Ok, now))
        assertTrue(c.body.predictions.isEmpty())
        assertNull(c.body.thresholdPace)
        assertTrue(WidgetMapper.fitness(FitnessSnapshot(stamp()), AuthState.Ok, now) is WidgetUi.Message)
        assertTrue(WidgetMapper.fitness(null, AuthState.NeedsSignIn, now) is WidgetUi.Message)
    }

    @Test fun trendFormat() {
        assertEquals("+2", Fmt.trend(2)); assertEquals("-3", Fmt.trend(-3)); assertEquals("steady", Fmt.trend(0))
        assertEquals("up 2", Fmt.trendSpoken("+2")); assertEquals("down 3", Fmt.trendSpoken("-3"))
    }
}
