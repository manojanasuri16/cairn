package app.cairn.widget

import app.cairn.core.model.Freshness
import app.cairn.sync.IndicatorKind
import app.cairn.sync.RunRecord
import app.cairn.sync.SyncState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAndFooterTest {
    private val now = Instant.parse("2030-01-04T12:00:00Z")
    private fun footer(tier: Freshness, ageMin: Long) = FooterUi("x", tier, "x", ageMin * 60_000L)

    @Test fun footerIndicatorStatesAndLabels() {
        val fresh = FooterRules.indicator(footer(Freshness.Fresh, 4), null, now)
        assertEquals(IndicatorKind.Fresh, fresh.kind); assertNull(FooterRules.label(fresh, 160f))
        val aging = FooterRules.indicator(footer(Freshness.Aging, 5 * 60), null, now)
        assertEquals("5h", FooterRules.label(aging, 160f))
        assertTrue(aging.description.contains("Updated 5h ago"))
        val stale = FooterRules.indicator(footer(Freshness.Stale, 3 * 24 * 60), null, now)
        assertEquals("3d", FooterRules.label(stale, 160f))
        val syncing = FooterRules.indicator(footer(Freshness.Stale, 3 * 24 * 60), SyncState(userSyncRequestedMs = now.minusSeconds(3).toEpochMilli()), now)
        assertEquals("Syncing…", FooterRules.label(syncing, 160f))
        assertEquals(R.drawable.ic_w_sync_busy, FooterRules.iconRes(syncing.kind))
        val err = FooterRules.indicator(footer(Freshness.Fresh, 1), SyncState(runs = listOf(RunRecord(now.minusSeconds(20).toEpochMilli(), "USER", "FAILED"))), now)
        assertEquals("Couldn't sync", FooterRules.label(err, 160f))
        assertEquals("Couldn't sync, tap to retry", FooterRules.label(err, 300f))
    }

    @Test fun syncIconHidesOnlyOnTinyCellsAndOnlyWhenAsked() {
        assertTrue(FooterRules.showSyncIcon(false, 110f, 110f))
        assertFalse(FooterRules.showSyncIcon(true, 110f, 110f))
        assertTrue(FooterRules.showSyncIcon(true, 250f, 110f))
        assertTrue(FooterRules.showSyncIcon(true, 110f, 200f))
        assertTrue(FooterRules.BUTTON_W_DP >= 40f)
        assertTrue(WidgetConfig.decode(WidgetConfig(hideSyncIcon = true).encode(), WidgetKind.Today).hideSyncIcon)
        assertFalse(WidgetConfig.decode(WidgetConfig().encode(), WidgetKind.Today).hideSyncIcon)
    }

    @Test fun everyKindAndSizeResolvesToItsOwnReceiverAndBack() {
        for (k in WidgetKind.entries) for (s in WidgetSize.entries) {
            val name = WidgetVariants.receiverName(k, s)
            assertEquals("$k $s", k, WidgetVariants.kindOf(name))
            assertEquals("$k $s", s, WidgetVariants.sizeOf("app.cairn.widget.$name"))
            assertEquals(name, WidgetVariants.receiverClass(k, s).simpleName)
        }
        // Original receivers keep their names and their natural size.
        assertEquals("TodayWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Today, WidgetSize.Small))
        assertEquals("OverviewWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Overview, WidgetSize.Wide))
        assertEquals("TodayWideWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Today, WidgetSize.Wide))
        assertEquals("Today (4x2)", WidgetVariants.pickerLabel(WidgetKind.Today, WidgetSize.Wide))
        assertEquals(WidgetKind.TrainingLoad, WidgetVariants.kindOf("TrainingLoadLargeWidgetReceiver"))
        assertNull(WidgetVariants.kindOf("SomethingElse"))
        assertEquals(setOf(2 to 2, 4 to 2, 4 to 3), WidgetSize.entries.map { it.cw to it.ch }.toSet())
    }

    private class Kv : PinKv {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
    }

    @Test fun pendingPinIsClaimedOnceAndExpires() {
        val pins = PendingPins(Kv())
        val cfg = WidgetConfig(style = WidgetStyle.TodayStack, compactFooter = true)
        pins.put(PendingPin(WidgetKind.Today, WidgetSize.Wide, cfg, 1_000L))
        val got = pins.claim(WidgetKind.Today, 2_000L)
        assertNotNull(got); assertEquals(WidgetSize.Wide, got!!.size); assertEquals(WidgetStyle.TodayStack, got.config.style); assertTrue(got.config.compactFooter)
        assertNull("claimed once", pins.claim(WidgetKind.Today, 2_000L))
        pins.put(PendingPin(WidgetKind.Today, WidgetSize.Wide, cfg, 1_000L))
        assertNull("after the TTL", pins.claim(WidgetKind.Today, 1_000L + PendingPins.TTL_MS + 1))
        // Another kind is untouched; another size is left for its own widget.
        pins.put(PendingPin(WidgetKind.Sleep, WidgetSize.Large, cfg, 5_000L))
        assertNull(pins.claim(WidgetKind.Today, 5_100L))
        assertNull(pins.claim(WidgetKind.Sleep, 5_100L, onlySize = WidgetSize.Small))
        assertNotNull(pins.claim(WidgetKind.Sleep, 5_100L, onlySize = WidgetSize.Large))
        pins.markClaimed(7, 10_000L)
        assertTrue(pins.wasClaimedRecently(7, 11_000L)); assertFalse(pins.wasClaimedRecently(7, 10_000L + PendingPins.CLAIMED_WINDOW_MS + 1)); assertFalse(pins.wasClaimedRecently(8, 11_000L))
    }

    @Test fun labelsNeverEllipsizeToAStub() {
        // Wide system fonts: the estimate carries a safety factor of about 11 percent.
        assertEquals(1.11f, TextFit.WIDE_FONT_FACTOR, 0.001f)
        val base = "Sleep".sumOf { 0.58 }.toFloat() * 12f + 2f
        assertTrue(TextFit.widthDp("Sleep", 12f, 1f) > base * 1.05f)
        // Plenty of room: full label. Less: the short one. Almost none: null, which the row renders as icon only.
        assertEquals("Resting HR", Layouts.rowLabel("Resting HR", "52 bpm", 220f, 1f))
        assertEquals("RHR", Layouts.rowLabel("Resting HR", "52 bpm", 135f, 1f))
        assertNull(Layouts.rowLabel("Sleep", "6h 4m · 73", 100f, 1f))
        for (w in listOf(90f, 110f, 130f, 160f)) for (l in listOf("Sleep", "Resting HR", "Recovery", "Calories", "Stress")) {
            val r = Layouts.rowLabel(l, "6h 4m · 73", w, 1f)
            assertTrue("$l at $w", r == null || r == l || r == Layouts.shortLabel(l))
        }
    }
}
