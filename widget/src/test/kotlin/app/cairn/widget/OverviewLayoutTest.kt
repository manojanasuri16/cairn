package app.cairn.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewLayoutTest {
    private val values = listOf("1,234" to "kcal", "12.34" to "km", "7h 41m" to "", "10:45" to "/mi", "152" to "bpm", "8,432" to "steps", "1.12" to "")

    @Test fun patternsFollowCountAndAspect() {
        assertEquals(OverviewPattern.Single, OverviewLayout.pattern(1, 300f, 150f).first)
        assertEquals(Triple(OverviewPattern.Grid, 2, 1), OverviewLayout.pattern(2, 300f, 150f))
        assertEquals(Triple(OverviewPattern.Grid, 1, 2), OverviewLayout.pattern(2, 120f, 250f))
        assertEquals(OverviewPattern.BigLeft, OverviewLayout.pattern(3, 300f, 150f).first)
        assertEquals(OverviewPattern.BigTop, OverviewLayout.pattern(3, 150f, 250f).first)
        assertEquals(Triple(OverviewPattern.Grid, 2, 2), OverviewLayout.pattern(4, 250f, 170f))
        assertEquals(Triple(OverviewPattern.Grid, 4, 1), OverviewLayout.pattern(4, 330f, 90f))
        assertEquals(Triple(OverviewPattern.Grid, 3, 2), OverviewLayout.pattern(6, 300f, 170f))
        assertEquals(Triple(OverviewPattern.Grid, 2, 3), OverviewLayout.pattern(6, 160f, 250f))
        assertEquals(Triple(OverviewPattern.Grid, 3, 2), OverviewLayout.pattern(5, 300f, 170f))
    }

    @Test fun tilesFillTheBoxWithNoSlack() {
        val g = OverviewLayout.GAP
        for (n in 1..6) for (w in listOf(110f, 170f, 250f, 330f)) for (h in listOf(80f, 110f, 170f, 250f, 330f)) {
            val (p, c, r) = OverviewLayout.pattern(n, w, h)
            val (tw, th) = OverviewLayout.tileSize(n, p, c, r, w, h)
            val widest = when (p) { OverviewPattern.Single -> 1; OverviewPattern.Grid -> app.cairn.core.designsystem.FitMath.rows(n, c).max(); else -> 2 }
            val rowCount = when (p) { OverviewPattern.Single -> 1; OverviewPattern.Grid -> app.cairn.core.designsystem.FitMath.rows(n, c).size; else -> 2 }
            assertEquals("$n ${w}x$h widths", w, tw * widest + g * (widest - 1), 0.01f)
            assertEquals("$n ${w}x$h heights", h, th * rowCount + g * (rowCount - 1), 0.01f)
        }
    }

    @Test fun aTinyBoxShowsFewerStatsInsteadOfCrushingTiles() {
        val p = OverviewLayout.plan(6, 110f, 110f)
        assertTrue(p.shown < 6)
        assertTrue(p.tileW >= OverviewLayout.MIN_TILE_W || p.shown == 1)
        assertEquals(6, OverviewLayout.plan(6, 330f, 250f).shown)
        assertEquals(4, OverviewLayout.plan(4, 330f, 170f).shown)
    }

    @Test fun valuesAndUnitsFitTheirTileAtEveryFontScale() {
        for ((number, unit) in values) for (tw in listOf(72f, 90f, 120f, 160f, 330f)) for (th in listOf(52f, 80f, 120f, 200f)) for (s in listOf(1f, 1.3f, 2f)) {
            val (sp, usp) = OverviewLayout.valueSizes(number, unit, tw, th, s)
            val width = TextFit.widthDp(number, sp, s, Layouts.HERO_CAP) + (if (unit.isEmpty()) 0f else 2f + TextFit.widthDp(unit, usp, s, Layouts.HERO_CAP))
            assertTrue("'$number $unit' ${tw}x$th x$s at $sp needs $width", width <= tw - 2 * OverviewLayout.PAD || sp * TextFit.effectiveScale(s, Layouts.HERO_CAP) <= 14.01f)
            assertTrue(sp * TextFit.effectiveScale(s, Layouts.HERO_CAP) >= 13.9f || sp >= 14f)
            assertTrue(usp >= 10f)
        }
        // A bigger tile gets a bigger number.
        assertTrue(OverviewLayout.valueSizes("8,432", "steps", 330f, 200f, 1f).first > OverviewLayout.valueSizes("8,432", "steps", 100f, 60f, 1f).first)
    }

    @Test fun ringsNeverSqueezeTheValue() {
        for ((number, unit) in values) for (tw in listOf(90f, 140f, 330f)) for (s in listOf(1f, 2f)) {
            val d = OverviewLayout.ringSize(number, unit, tw, 100f, s)
            if (d > 0f) {
                val (sp, usp) = OverviewLayout.valueSizes(number, unit, tw, 100f, s, d + 6f)
                val width = TextFit.widthDp(number, sp, s, Layouts.HERO_CAP) + (if (unit.isEmpty()) 0f else 2f + TextFit.widthDp(unit, usp, s, Layouts.HERO_CAP))
                assertTrue("$number $unit $tw x$s ring $d", width <= tw - 2 * OverviewLayout.PAD - d - 6f + 0.5f || sp <= 14.01f)
            }
        }
    }

    @Test fun splitUnitKeepsNumberAndUnitApart() {
        assertEquals("52" to "bpm", OverviewMapper.splitUnit("52 bpm"))
        assertEquals("1,820" to "kcal", OverviewMapper.splitUnit("1,820 kcal"))
        assertEquals("98" to "%", OverviewMapper.splitUnit("98%"))
        assertEquals("5.6" to "km", OverviewMapper.splitUnit("5.6 km"))
        assertEquals("7h 41m" to "", OverviewMapper.splitUnit("7h 41m"))
    }

    @Test fun metricConfigRoundTripsAndIsCapped() {
        val cfg = WidgetConfig(metrics = listOf(OverviewId.Steps, OverviewId.LoadRatio, OverviewId.Vo2))
        assertEquals(cfg, WidgetConfig.decode(cfg.encode(), WidgetKind.Overview))
        assertEquals(OverviewId.Default, WidgetConfig().resolve(WidgetKind.Overview, AppAppearance()).metrics)
        val many = WidgetConfig(metrics = OverviewId.entries)
        assertEquals(6, many.resolve(WidgetKind.Overview, AppAppearance()).metrics.size)
        assertEquals(OverviewId.Default, WidgetConfig(metrics = emptyList()).resolve(WidgetKind.Overview, AppAppearance()).metrics)
        assertEquals(TapTarget.OpenCairn, TapTargets.default(WidgetKind.Overview))
    }

    @Test fun overviewShowsOnlyAvailableStatsAndTheOldestFooter() {
        val today = Samples.today(); val rec = Samples.recovery(); val sleep = Samples.sleep(); val load = Samples.load(); val fit = Samples.fitness(); val act = Samples.activity()
        val ui = OverviewMapper.build(today, sleep, rec, load, fit, act) as WidgetUi.Content
        assertTrue(OverviewId.Steps in ui.body.items.map { it.id } && OverviewId.Recovery in ui.body.items.map { it.id } && OverviewId.LastDistance in ui.body.items.map { it.id })
        val stale = rec.copy(footer = Samples.staleFooter)
        val mixed = OverviewMapper.build(today, sleep, stale, load, fit, act) as WidgetUi.Content
        assertEquals(Samples.staleFooter, mixed.footer)
    }

    @Test fun aDefaultFourStatWidgetShowsAllFourOnATypicalFourByTwoCell() {
        // 272 x 120 dp cell: content area after padding and footer is about 244 x 70.
        assertEquals(4, OverviewLayout.plan(4, 244f, 70f).shown)
        assertEquals(4, OverviewLayout.plan(4, 154f, 150f).shown)
        assertEquals(1, OverviewLayout.plan(1, 244f, 70f).shown)
    }

    @Test fun pinnedWidgetOpensConfigurationOnlyOnce() {
        assertTrue(ConfigLaunch.shouldLaunch(42, alreadyShown = false))
        assertTrue(!ConfigLaunch.shouldLaunch(42, alreadyShown = true))
        assertTrue(!ConfigLaunch.shouldLaunch(android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID, alreadyShown = false))
    }

    private val sizes = WidgetSizes.byKind.getValue(WidgetKind.Overview)

    @Test fun fourStatsAtATwoCellSizeFormATwoByTwo() {
        val p = OverviewLayout.planForCell(4, 175f, 222f, 1f, sizes)
        assertEquals(4, p.shown); assertEquals(OverviewPattern.Grid, p.pattern); assertEquals(2, p.cols); assertEquals(2, p.rows)
        assertTrue("tiles roughly square ${p.tileW}x${p.tileH}", p.tileW / p.tileH in 0.6f..1.4f)
    }

    @Test fun sixStatsAtAFourByTwoSizeFormAThreeByTwo() {
        val p = OverviewLayout.planForCell(6, 272f, 223f, 1f, sizes)
        assertEquals(6, p.shown); assertEquals(3, p.cols); assertEquals(2, p.rows)
    }

    @Test fun fewerStatsFitInOrderNeverSkippingTheMiddle() {
        val chosen = listOf(OverviewId.Steps, OverviewId.Sleep, OverviewId.Recovery, OverviewId.RestingHr, OverviewId.Calories, OverviewId.Stress)
        for (n in 1..6) for ((w, h) in listOf(110f to 110f, 175f to 222f, 272f to 120f, 272f to 223f, 330f to 330f)) {
            val p = OverviewLayout.planForCell(n, w, h, 1f, sizes)
            assertEquals("$n ${w}x$h", chosen.take(n).take(p.shown), OverviewLayout.firstN(chosen.take(n), p.shown))
            assertTrue(p.shown in 1..n)
        }
    }

    @Test fun tilesAreAsTallAsTheirContentPlusPaddingNotStretched() {
        // The grid fills the box exactly, and every tile is sized so its header, gap and value fit with padding: no dead band.
        for (n in 1..6) for ((w, h) in listOf(175f to 222f, 272f to 223f, 330f to 330f)) {
            val p = OverviewLayout.planForCell(n, w, h, 1f, sizes)
            if (p.shown == 1) continue
            val (sp, _) = OverviewLayout.valueSizes("8,432", "steps", p.tileW, p.tileH, 1f)
            val content = 12f * 1.2f + 4f + sp * 1.2f
            assertTrue("$n ${w}x$h tile ${p.tileH} cannot hold $content", p.tileH >= content + 2 * OverviewLayout.PAD - 0.5f)
            // The number grows with the tile, so a taller tile is filled by a bigger number, not by empty space.
            assertTrue(sp >= 14f)
        }
        assertTrue(OverviewLayout.valueSizes("82", "%", 120f, 120f, 1f).first > OverviewLayout.valueSizes("82", "%", 120f, 60f, 1f).first)
    }

    @Test fun typicalLauncherReportedSizesShowTheDefaultFour() {
        // Launchers report a widget a little under its cell: a 2x2 as about 146-176 x 180-223 dp, a 4x2 as about 272-320 x 110-146.
        for (w in listOf(146f, 150f, 164f, 176f)) for (h in listOf(180f, 200f, 223f)) {
            val p = OverviewLayout.planForCell(4, w, h, 1f, sizes)
            assertEquals("2x2 cell reported ${w}x$h", 4, p.shown); assertEquals(2, p.cols); assertEquals(2, p.rows)
        }
        for (w in listOf(272f, 300f, 320f)) for (h in listOf(110f, 130f, 146f)) assertEquals("4x2 cell reported ${w}x$h", 4, OverviewLayout.planForCell(4, w, h, 1f, sizes).shown)
        for (w in listOf(272f, 320f)) for (h in listOf(55f, 75f)) assertTrue(OverviewLayout.planForCell(4, w, h, 1f, sizes).shown >= 1)
        // The size a variant is registered at never exceeds the size its rule assumes (rule size = registered + margin).
        assertTrue(WidgetSizes.MARGIN_DP > 0f)
    }
}
