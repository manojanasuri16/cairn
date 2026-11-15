package app.cairn.widget

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.SizeClass
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.designsystem.WidgetTokens
import app.cairn.core.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantLayoutTest {
    private val sizes = WidgetCatalogSizes.all + listOf(160f to 160f, 250f to 110f, 320f to 320f, 110f to 220f)
    private val scales = listOf(1f, 1.3f, 2f)

    @Test fun everyVariantFitsEverySizeClass() {
        for (style in WidgetStyle.entries.filter { it.kind != WidgetKind.Overview }) for ((w, h) in sizes) for (s in scales) {
            val p = Layouts.plan(style, w, h, s)
            val tag = "$style ${w}x$h x$s"
            // Never clips: secondary blocks only appear while they fit, so what is used stays within the available height
            // (the hero alone may exceed it in a cell too small for anything else).
            if (p.blocks.isNotEmpty() || p.heroDp > 0) assertTrue("$tag used ${p.usedDp} > ${p.availDp}", p.usedDp <= p.availDp + 0.5f || p.blocks.size <= 1)
            assertTrue("$tag hero ${p.heroSp}", p.heroSp >= WidgetTokens.MIN_RING_NUMERAL_SP)
            if (p.heroDp == 0 && style != WidgetStyle.TodayStripe && style != WidgetStyle.FitnessGrid && style != WidgetStyle.FitnessLadder && style != WidgetStyle.ActivityGrid && p.style != WidgetStyle.ActivityLine) {
                assertTrue("$tag text hero below legible ${p.heroSp}", p.heroSp >= WidgetTokens.MIN_HERO_SP)
            }
            if (p.heroDp > 0) assertTrue("$tag graphic ${p.heroDp} wider than cell", p.heroDp <= w - 2 * p.pad + 1 || style in listOf(WidgetStyle.LoadBars, WidgetStyle.LoadTrend))
        }
    }

    @Test fun biggerCellsAddContentOrScaleTheHero() {
        for (style in WidgetStyle.entries.filter { it.kind != WidgetKind.Overview && (it.kind != WidgetKind.LastActivity || it == WidgetStyle.ActivityHero) }) {
            val small = Layouts.plan(style, 160f, 160f, 1f)
            val big = Layouts.plan(style, 320f, 320f, 1f)
            val more = big.blocks.size > small.blocks.size || big.heroSp > small.heroSp || big.heroDp > small.heroDp || big.perRow > small.perRow
            assertTrue("$style does not use the larger cell", more)
        }
    }

    @Test fun heroNeverShrinksBelowLegibleAtLargeFonts() {
        for (style in WidgetStyle.entries) {
            val p = Layouts.plan(style, 250f, 160f, 2f)
            assertTrue("$style ${p.heroSp}", p.heroSp >= WidgetTokens.MIN_RING_NUMERAL_SP)
        }
        assertTrue(Layouts.heroSp(SizeClass.Small, 2f) >= WidgetTokens.MIN_HERO_SP)
    }

    @Test fun largeFontsDropLowPriorityRowsFirst() {
        for (style in listOf(WidgetStyle.TodayStack, WidgetStyle.SleepBar, WidgetStyle.FitnessVo2, WidgetStyle.SleepTimeline)) {
            assertTrue("$style", Layouts.plan(style, 160f, 200f, 2f).blocks.size <= Layouts.plan(style, 160f, 200f, 1f).blocks.size)
        }
        assertTrue(Layouts.plan(WidgetStyle.TodayRing, 150f, 220f, 2f).count("r") < Layouts.plan(WidgetStyle.TodayRing, 150f, 220f, 1f).count("r"))
    }

    @Test fun blocksAreAddedInPriorityOrder() {
        val p = Layouts.plan(WidgetStyle.SleepBar, 160f, 160f, 1f)
        val order = listOf("score", "bar", "window", "legend", "note")
        val present = order.map { it in p.blocks }
        assertEquals(present.sortedByDescending { it }, present) // no later block without the earlier ones
    }

    @Test fun todayRingBreakpoints() {
        val tiny = Layouts.plan(WidgetStyle.TodayRing, 110f, 110f, 1f)
        assertFalse(tiny.side); assertEquals(0, tiny.count("r"))
        val wide = Layouts.plan(WidgetStyle.TodayRing, 250f, 110f, 1f)
        assertTrue(wide.side); assertTrue(wide.count("r") >= 1)
        assertTrue(Layouts.plan(WidgetStyle.TodayRing, 250f, 220f, 1f).count("r") > wide.count("r"))
        assertTrue(Layouts.plan(WidgetStyle.TodayRing, 150f, 240f, 1f).count("r") <= Layouts.MAX_ROWS)
    }

    @Test fun stripeWrapsIntoMoreLinesWhenTall() {
        val short = Layouts.plan(WidgetStyle.TodayStripe, 250f, 100f, 1f)
        val tall = Layouts.plan(WidgetStyle.TodayStripe, 160f, 250f, 1f)
        assertTrue(short.count("c") >= 3)
        assertTrue(tall.count("c") >= short.perRow)
    }

    @Test fun activityStripIsAOneLinerForEveryStyle() {
        for (s in WidgetStyle.forKind(WidgetKind.LastActivity).filter { it != WidgetStyle.ActivityGrid }) assertEquals(WidgetStyle.ActivityLine, Layouts.plan(s, 250f, 70f, 1f).style)
        assertEquals(WidgetStyle.ActivityGrid, Layouts.plan(WidgetStyle.ActivityGrid, 250f, 70f, 1f).style)
        assertEquals(WidgetStyle.ActivityGrid, Layouts.plan(WidgetStyle.ActivityGrid, 250f, 160f, 1f).style)
        assertTrue(Layouts.plan(WidgetStyle.ActivityLine, 250f, 40f, 1f).pad <= 6)
    }

    @Test fun gridsUseMoreColumnsWhenWide() {
        assertEquals(2, Layouts.plan(WidgetStyle.ActivityGrid, 160f, 200f, 1f).perRow)
        assertEquals(4, Layouts.plan(WidgetStyle.ActivityGrid, 300f, 120f, 1f).perRow)
        assertEquals(2, Layouts.plan(WidgetStyle.FitnessGrid, 160f, 200f, 1f).perRow)
        assertEquals(4, Layouts.plan(WidgetStyle.FitnessGrid, 300f, 140f, 1f).perRow)
    }

    @Test fun ringNumeralsFitInsideTheRing() {
        for (ring in listOf(56, 72, 104, 150)) {
            val sp = Layouts.heroSp(SizeClass.Small, 1f).let { Layouts.plan(WidgetStyle.RecoveryRing, ring + 40f, ring + 60f, 1f, chars = 6).heroSp }
            assertTrue(sp * 0.56f * 6 <= (ring + 40f) || sp <= WidgetTokens.MIN_RING_NUMERAL_SP + 0.01f)
        }
    }

    @Test fun predictionPaceMath() {
        assertEquals("4:00 /km", Layouts.predictionPace("5K", "20:00", DistanceUnit.Km))
        assertEquals("4:00 /km", Layouts.predictionPace("10K", "40:00", DistanceUnit.Km))
        assertNotNull(Layouts.predictionPace("HM", "1:39:30", DistanceUnit.Mi))
        assertNull(Layouts.predictionPace("Mile", "5:00", DistanceUnit.Km))
        assertEquals(3 * 3600 + 30 * 60 + 12, Layouts.parseRaceTime("3:30:12"))
        assertNull(Layouts.parseRaceTime("abc"))
    }

    // ---- configuration ----

    @Test fun configRoundTripsAndToleratesGarbage() {
        val cfg = WidgetConfig(
            ThemeId.Dawn, WidgetStyle.RecoveryGauge, Bg.Solid, 70, Corners.ExtraRound, AccentOption.Violet,
            setOf(Part.TimeToFull), UnitsPref.Mi, compactFooter = true,
        )
        assertEquals(cfg, WidgetConfig.decode(cfg.encode(), WidgetKind.Recovery))
        assertEquals(WidgetConfig(), WidgetConfig.decode(null, WidgetKind.Today))
        assertEquals(WidgetConfig(), WidgetConfig.decode("", WidgetKind.Today))
        val bad = WidgetConfig.decode("theme=Nope;style=SleepBar;op=abc;hide=Zzz,Pace;;x", WidgetKind.Today)
        assertNull(bad.theme); assertNull(bad.style); assertNull(bad.opacity)
        assertEquals(setOf(Part.Pace), bad.hidden)
    }

    @Test fun unknownStyleFallsBackToKindDefault() {
        val r = WidgetConfig(style = WidgetStyle.SleepBar).resolve(WidgetKind.Today, AppAppearance())
        assertEquals(WidgetStyle.TodayRing, r.style)
        for (k in WidgetKind.entries) assertEquals(if (k == WidgetKind.Overview) 1 else 3, WidgetStyle.forKind(k).size)
    }

    @Test fun placedWidgetsInheritTheAppAppearance() {
        val app = AppAppearance(ThemeId.Berry, Bg.Solid, 80)
        val r = WidgetConfig().resolve(WidgetKind.Sleep, app)
        assertEquals(ThemeId.Berry, r.theme); assertEquals(Bg.Solid, r.bg); assertEquals(80, r.opacity)
        val own = WidgetConfig(theme = ThemeId.Ocean, bg = Bg.Gradient, opacity = 40).resolve(WidgetKind.Sleep, app)
        assertEquals(ThemeId.Ocean, own.theme); assertEquals(40, own.opacity)
    }

    @Test fun transparentUsesWallpaperInkAndZeroOpacity() {
        val r = WidgetConfig(bg = Bg.Transparent).resolve(WidgetKind.Today, AppAppearance())
        assertEquals(0, r.opacity); assertTrue(r.onWallpaper)
        assertTrue(WidgetConfig(opacity = 30).resolve(WidgetKind.Today, AppAppearance()).onWallpaper)
        assertFalse(WidgetConfig(opacity = 90).resolve(WidgetKind.Today, AppAppearance()).onWallpaper)
    }

    @Test fun hiddenPartsOnlyCountForTheirOwnKind() {
        val r = WidgetConfig(hidden = setOf(Part.Sleep, Part.Pace)).resolve(WidgetKind.Today, AppAppearance())
        assertEquals(setOf(Part.Sleep), r.hidden)
        assertFalse(r.shows(Part.Sleep)); assertTrue(r.shows(Part.RestingHr))
    }

    @Test fun everyKindHasTogglesAndFooterIsNotOne() {
        for (k in WidgetKind.entries) assertTrue(Part.forKind(k).isNotEmpty())
        assertTrue(Part.entries.none { it.name.contains("Footer", ignoreCase = true) || it.label.contains("footer", ignoreCase = true) || it.label == "Data age" })
    }
}

/** The responsive breakpoint grid, as plain floats (the Glance set is built from the same numbers). */
object WidgetCatalogSizes {
    val all: List<Pair<Float, Float>> = listOf(110f, 170f, 250f, 320f).flatMap { w -> listOf(70f, 110f, 170f, 250f).map { h -> w to h } }
}
