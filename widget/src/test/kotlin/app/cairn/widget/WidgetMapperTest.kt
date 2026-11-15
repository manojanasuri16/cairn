package app.cairn.widget

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.Freshness
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMapperTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")
    private val us = Locale.US

    private fun ago(h: Long, m: Long = 0) = now.minus(Duration.ofHours(h).plusMinutes(m))
    private fun <T> sv(v: T, at: Instant, src: DataSource = DataSource.COROS_MCP) = SourcedValue(v, src, at)
    private fun stamp(at: Instant, src: DataSource = DataSource.COROS_MCP) = Stamp(src, at)

    @Test fun formats() {
        assertEquals("8 h 13 min", Fmt.duration(493))
        assertEquals("45 min", Fmt.duration(45))
        assertEquals("8 h", Fmt.duration(480))
        assertEquals("7.20 km", Fmt.distanceKm(7200.0, us))
        assertEquals("5'13\" /km", Fmt.pace(313))
        assertEquals("5'05\" /km", Fmt.pace(305))
        assertEquals("8,432", Fmt.steps(8432, us))
        assertEquals("Trail run", Fmt.sport("TRAIL_RUN"))
        assertEquals("28 hrs till full recovery", Fmt.timeToFull(28))
        assertNull(Fmt.timeToFull(0))
    }

    @Test fun footerTiers() {
        val fresh = WidgetMapper.footer(listOf(ago(2)), now)
        assertEquals(Freshness.Fresh, fresh.tier)
        assertEquals("Updated 2h ago", fresh.text)
        assertFalse(fresh.opensCoros)
        val aging = WidgetMapper.footer(listOf(ago(5)), now)
        assertEquals(Freshness.Aging, aging.tier)
        val stale = WidgetMapper.footer(listOf(ago(14)), now)
        assertEquals(Freshness.Stale, stale.tier)
        assertEquals("Stale, last synced 14h ago", stale.text)
        assertTrue(stale.opensCoros)
        // Newest stamp wins.
        assertEquals(Freshness.Fresh, WidgetMapper.footer(listOf(ago(30), ago(1)), now).tier)
    }

    @Test fun todayFull() {
        val d = DailySummary(
            today, steps = sv(8432, ago(1)), calories = sv(2100, ago(1)), avgStress = sv(31, ago(1)), restingHr = sv(52, ago(1)),
        )
        val s = SleepNight(today, stamp(ago(4)), score = 82, asleepMinutes = 433)
        val r = Recovery(stamp(ago(1)), percent = 74)
        val ui = WidgetMapper.today(d, s, r, AuthState.Ok, now, today, us) as WidgetUi.Content
        assertEquals("8,432", ui.body.stepsText)
        assertEquals(0.8432f, ui.body.stepsFraction, 0.0001f)
        assertEquals(listOf(MetricId.Sleep, MetricId.RestingHr, MetricId.Recovery, MetricId.Calories, MetricId.Stress), ui.body.metrics.map { it.id })
        assertEquals("7 h 13 min · 82", ui.body.metrics[0].value)
        assertEquals(Freshness.Fresh, ui.footer.tier)
        assertTrue(ui.description.contains("8,432 steps"))
        assertTrue(ui.description.contains("Updated 1h ago"))
    }

    @Test fun todayHealthConnectOnlyHidesMissing() {
        val d = DailySummary(today, steps = sv(3000, ago(1), DataSource.HEALTH_CONNECT))
        val ui = WidgetMapper.today(d, null, null, AuthState.SignedOut, now, today, us) as WidgetUi.Content
        assertTrue(ui.body.metrics.isEmpty())
        assertEquals("3,000", ui.body.stepsText)
    }

    @Test fun todayPreviousDaySummaryIsNotToday() {
        val d = DailySummary(today.minusDays(1), steps = sv(9000, ago(20)))
        val r = Recovery(stamp(ago(2)), percent = 60)
        val ui = WidgetMapper.today(d, null, r, AuthState.Ok, now, today, us) as WidgetUi.Content
        assertNull(ui.body.stepsText)
        assertEquals(listOf(MetricId.Recovery), ui.body.metrics.map { it.id })
    }

    @Test fun emptyAndAuthStates() {
        assertEquals(MessageKind.Empty, (WidgetMapper.today(null, null, null, AuthState.Ok, now, today) as WidgetUi.Message).kind)
        val signedOut = WidgetMapper.today(null, null, null, AuthState.SignedOut, now, today) as WidgetUi.Message
        assertEquals(MessageKind.SignedOut, signedOut.kind)
        assertEquals(Screen.Account, signedOut.screen)
        val needs = WidgetMapper.recovery(null, AuthState.NeedsSignIn, now) as WidgetUi.Message
        assertEquals("Sign in again", needs.title)
        assertEquals(Screen.Activities, (WidgetMapper.lastActivity(null, AuthState.Ok, now, today) as WidgetUi.Message).screen)
    }

    @Test fun recoveryLevels() {
        val ui = WidgetMapper.recovery(Recovery(stamp(ago(14)), percent = 64, hoursToFullRecovery = 9), AuthState.Ok, now) as WidgetUi.Content
        assertEquals("64%", ui.body.percentText)
        assertEquals("Recovering", ui.body.level)
        assertEquals("9 hrs till full recovery", ui.body.timeToFull)
        assertEquals(Freshness.Stale, ui.footer.tier)
        val full = WidgetMapper.recovery(Recovery(stamp(ago(1)), percent = 100, level = "FULLY_RECOVERED"), AuthState.Ok, now) as WidgetUi.Content
        assertEquals("Fully recovered", full.body.level)
        assertEquals("Fully recovered", full.body.timeToFull)
    }

    @Test fun lastActivity() {
        val a = Activity("x1", stamp(ago(2)), today.minusDays(1), "run", durationSec = 2460, distanceMeters = 7200.0, avgPaceSecPerKm = 313, avgHr = 151)
        val ui = WidgetMapper.lastActivity(a, AuthState.Ok, now, today, us) as WidgetUi.Content
        assertEquals(ActivityUi("Run", "Yesterday", "7.20 km", "41 min", "5'13\" /km", "151 bpm"), ui.body)
        val bare = WidgetMapper.lastActivity(Activity("x2", stamp(ago(2)), today, "gym"), AuthState.Ok, now, today, us) as WidgetUi.Content
        assertNull(bare.body.distance); assertNull(bare.body.pace); assertNull(bare.body.avgHr)
    }

    @Test fun sizeRules() {
        assertEquals(10f, SizeRules.cappedSp(10f, 1f), 0f)
        assertEquals(6.5f, SizeRules.cappedSp(10f, 2f), 0.001f) // 6.5sp * 2.0 scale = 13 = 10 * 1.3
        assertEquals(1, SizeRules.visibleRows(30f, 2f))
        assertEquals(4, SizeRules.visibleRows(100f, 1f))
        assertEquals(2, SizeRules.visibleRows(100f, 2f))
    }
}
