package app.cairn.widget

import app.cairn.core.model.DataSource
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRulesTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")

    @Test fun loadRatioShowsTwoDecimals() {
        val d = TrainingLoadDay(today, Stamp(DataSource.COROS_MCP, now), shortTermLoad = 100, loadRatio = 0.47, comment = null)
        val c = WidgetMapper.trainingLoad(listOf(d), null, null, AuthState.Ok, now, today, Locale.US) as WidgetUi.Content
        assertEquals("0.47", c.body.ratioText)
        assertTrue(c.description.contains("Load ratio 0.47"))
        val de = WidgetMapper.trainingLoad(listOf(d), null, null, AuthState.Ok, now, today, Locale.GERMANY) as WidgetUi.Content
        assertEquals("0,47", de.body.ratioText)
    }

    @Test fun todayBreakpoints() {
        assertEquals(SizeRules.TodayMode.RingOnly, SizeRules.today(100f, 100f, 1f).mode)
        assertEquals(SizeRules.TodayMode.RingOnly, SizeRules.today(100f, 200f, 1f).mode) // too narrow for labels
        val cell = SizeRules.today(150f, 150f, 1f)
        assertEquals(SizeRules.TodayMode.Stacked, cell.mode)
        assertTrue(cell.rows >= 2)
        val bigger = SizeRules.today(150f, 240f, 1f)
        assertTrue(bigger.rows > cell.rows)
        assertTrue(bigger.rows <= SizeRules.MAX_TODAY_ROWS)
        val wide = SizeRules.today(250f, 110f, 1f)
        assertEquals(SizeRules.TodayMode.Side, wide.mode)
        assertTrue(wide.rows >= 1)
        assertTrue(SizeRules.today(250f, 200f, 1f).rows > wide.rows)
    }

    @Test fun todayDropsRowsAtLargeFont() {
        assertTrue(SizeRules.today(150f, 180f, 2f).rows < SizeRules.today(150f, 180f, 1f).rows)
        assertTrue(SizeRules.today(250f, 110f, 2f).rows >= 1)
    }

    @Test fun sleepBreakpoints() {
        val small = SizeRules.sleep(110f, 110f, 1f)
        assertFalse(small.wide)
        assertTrue(small.score)
        val cell = SizeRules.sleep(150f, 160f, 1f)
        assertTrue(cell.score && cell.bar && cell.window)
        assertFalse(SizeRules.sleep(150f, 110f, 1f).legend)
        val wide = SizeRules.sleep(250f, 110f, 1f)
        assertTrue(wide.wide && wide.score && wide.window && wide.bar)
        assertTrue(SizeRules.sleep(250f, 160f, 1f).legend)
        val big = SizeRules.sleep(250f, 220f, 1f)
        assertTrue(big.legend && big.note)
    }

    @Test fun sleepDropsLowPriorityAtLargeFont() {
        val l = SizeRules.sleep(150f, 160f, 2f)
        assertFalse(l.legend)
        assertFalse(l.note)
    }

    @Test fun lastActivityBreakpoints() {
        assertEquals(SizeRules.ActivityMode.OneLine, SizeRules.lastActivity(250f, 40f, 1f).mode)
        val cell = SizeRules.lastActivity(150f, 150f, 1f)
        assertEquals(SizeRules.ActivityMode.Card, cell.mode)
        assertEquals(2, cell.statsPerRow)
        assertTrue(cell.maxStats >= 3) // duration, pace and heart rate all fit
        val wide = SizeRules.lastActivity(250f, 110f, 1f)
        assertEquals(3, wide.statsPerRow)
        assertTrue(wide.maxStats >= 3)
        assertTrue(SizeRules.lastActivity(150f, 150f, 2f).maxStats < 3)
    }

    @Test fun footerShortText() {
        val stale = WidgetMapper.footer(listOf(now.minusSeconds(14 * 3600L)), now)
        assertTrue(stale.shortText.startsWith("Stale "))
        assertTrue(stale.text.length > stale.shortText.length)
        val fresh = WidgetMapper.footer(listOf(now.minusSeconds(2 * 3600L)), now)
        assertEquals("2h ago", fresh.shortText)
    }
}
