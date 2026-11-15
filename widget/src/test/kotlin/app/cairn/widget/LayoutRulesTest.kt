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

    @Test fun footerShortText() {
        val stale = WidgetMapper.footer(listOf(now.minusSeconds(14 * 3600L)), now)
        assertTrue(stale.shortText.startsWith("Stale "))
        assertTrue(stale.text.length > stale.shortText.length)
        val fresh = WidgetMapper.footer(listOf(now.minusSeconds(2 * 3600L)), now)
        assertEquals("2h ago", fresh.shortText)
    }
}
