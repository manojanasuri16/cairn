package app.cairn.widget

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.Palettes
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewWidgetLayoutTest {
    private val kinds = listOf(WidgetKind.Hrv, WidgetKind.HeartRate, WidgetKind.Stress, WidgetKind.WeeklyMileage)
    private val cells = listOf(110f to 110f, 176f to 223f, 272f to 223f, 320f to 330f, 320f to 110f, 110f to 220f)
    private val scales = listOf(1f, 1.3f, 2f)

    private fun plan(style: WidgetStyle, w: Float, h: Float, s: Float) = SamplePlans.plan(style, w, h, s)

    @Test fun everyKindHasThreeStylesWithTheDefaultFirst() {
        for (k in kinds) assertEquals(3, WidgetStyle.forKind(k).size)
        assertEquals(WidgetStyle.HrvRange, WidgetStyle.default(WidgetKind.Hrv))
        assertEquals(WidgetStyle.HeartSpark, WidgetStyle.default(WidgetKind.HeartRate))
        assertEquals(WidgetStyle.StressPill, WidgetStyle.default(WidgetKind.Stress))
        assertEquals(WidgetStyle.MileageBars, WidgetStyle.default(WidgetKind.WeeklyMileage))
        assertEquals(listOf("Range bar", "7 nights", "Number and pill"), WidgetStyle.forKind(WidgetKind.Hrv).map { it.label })
        assertEquals(listOf("Number and level", "7-day bars", "Gauge arc"), WidgetStyle.forKind(WidgetKind.Stress).map { it.label })
    }

    @Test fun nothingOverflowsAndTheHeroStaysLegibleAtEverySizeAndFont() {
        for (k in kinds) for (style in WidgetStyle.forKind(k)) for ((w, h) in cells) for (s in scales) {
            val p = plan(style, w + WidgetSizes.MARGIN_DP, h + WidgetSizes.MARGIN_DP, s)
            val tag = "$style ${w}x$h x$s"
            if (p.blocks.size > 1) assertTrue("$tag used ${p.usedDp} > ${p.availDp}", p.usedDp <= p.availDp + 0.5f)
            assertTrue("$tag hero ${p.heroSp}", p.heroSp * TextFit.effectiveScale(s, Layouts.HERO_CAP) >= 13.9f)
            assertTrue("$tag graphic ${p.heroDp} wider than the cell", p.heroDp <= w + WidgetSizes.MARGIN_DP - 2 * p.pad + 1)
        }
    }

    @Test fun theHeroTextFitsItsWidthSoValuesNeverEllipsize() {
        for ((style, text) in listOf(WidgetStyle.HrvRange to "65 ms", WidgetStyle.HeartSpark to "151 bpm", WidgetStyle.MileageBars to "125.10 km", WidgetStyle.StressPill to "99")) for ((w, h) in cells) {
            val p = plan(style, w + 8f, h + 8f, 1f).let { Layouts.plan(style, w + 8f, h + 8f, 1f, heroText = text, values = listOf("Elevated")) }
            val avail = if (p.side) p.heroAvailDp else (w + 8f - 2 * p.pad)
            assertTrue("$style ${w}x$h sp ${p.heroSp}", p.heroSp <= 14.5f || TextFit.fits(text, p.heroSp, 1f, avail, Layouts.HERO_CAP))
        }
    }

    @Test fun theLevelWordAlwaysStaysNextToTheNumber() {
        // The assessment and level words are never colour only: at every cell the pill (or the gauge's level line) is planned.
        for ((style, key) in listOf(WidgetStyle.HrvRange to "pill", WidgetStyle.HrvNights to "pill", WidgetStyle.HrvPill to "pill", WidgetStyle.StressPill to "pill", WidgetStyle.StressBars to "pill")) {
            for ((w, h) in cells) assertTrue("$style ${w}x$h", plan(style, w + 8f, h + 8f, 1f).has(key))
        }
        for ((w, h) in cells) {
            val g = plan(WidgetStyle.StressGauge, w + 8f, h + 8f, 1f)
            assertTrue("gauge ${w}x$h", g.has("level") || g.has("pill"))
        }
    }

    @Test fun wideCellsPutTheChartBesideTheHero() {
        for (style in listOf(WidgetStyle.HrvRange, WidgetStyle.HrvNights, WidgetStyle.HeartSpark, WidgetStyle.HeartRange, WidgetStyle.StressBars, WidgetStyle.MileageBars, WidgetStyle.MileageTrend, WidgetStyle.MileageGoal)) {
            assertTrue("$style wide", plan(style, 328f, 118f, 1f).side)
            assertFalse("$style narrow", plan(style, 118f, 223f, 1f).side)
        }
    }

    @Test fun chartsGrowIntoSpareHeightSoNoBandStaysEmpty() {
        for (style in listOf(WidgetStyle.HrvNights, WidgetStyle.StressBars, WidgetStyle.MileageBars)) {
            val short = plan(style, 190f, 200f, 1f); val tall = plan(style, 190f, 330f, 1f)
            assertTrue("$style ${short.heroDp} -> ${tall.heroDp}", tall.heroDp >= short.heroDp && tall.heroDp > 0)
        }
        val p = plan(WidgetStyle.MileageBars, 190f, 330f, 1f)
        assertTrue("${p.usedDp} of ${p.availDp}", p.usedDp >= p.availDp - 60f)
    }

    @Test fun biggerCellsShowMoreOrScale() {
        for (k in kinds) for (style in WidgetStyle.forKind(k)) {
            val small = plan(style, 118f, 118f, 1f); val big = plan(style, 328f, 338f, 1f)
            assertTrue("$style", big.blocks.size > small.blocks.size || big.heroSp > small.heroSp || big.heroDp > small.heroDp || big.side)
        }
    }

    @Test fun largeFontsDropLowPriorityRowsFirst() {
        for (style in listOf(WidgetStyle.HrvPill, WidgetStyle.StressPill, WidgetStyle.MileageBars, WidgetStyle.HeartSpark)) {
            assertTrue("$style", plan(style, 168f, 208f, 2f).blocks.size <= plan(style, 168f, 208f, 1f).blocks.size)
        }
    }

    @Test fun goalRingFallsBackToANumberAndBarWhenTooShortForARing() {
        val tiny = plan(WidgetStyle.MileageGoal, 118f, 118f, 1f)
        assertEquals(0, tiny.heroDp); assertTrue(tiny.has("goal") || tiny.has("bar"))
        val tall = plan(WidgetStyle.MileageGoal, 176f, 231f, 1f)
        assertTrue(tall.heroDp >= 64 && tall.has("total"))
        val wide = plan(WidgetStyle.MileageGoal, 280f, 160f, 1f)
        assertTrue(wide.side && wide.heroDp >= 48)
    }

    @Test fun peerSizeRulesCoverTheNewKinds() {
        for (k in kinds) {
            val s = WidgetSizes.byKind.getValue(k)
            assertTrue("$k ${s.size}", s.size in 1..16); assertTrue(110 to 110 in s)
            assertTrue("$k default", WidgetVariants.defaultSize(k) in WidgetSize.entries)
        }
        assertEquals("HrvWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Hrv, WidgetSize.Small))
        assertEquals("HrvWideWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Hrv, WidgetSize.Wide))
        assertEquals("HeartRateLargeWidgetReceiver", WidgetVariants.receiverName(WidgetKind.HeartRate, WidgetSize.Large))
        assertEquals("StressWidgetReceiver", WidgetVariants.receiverName(WidgetKind.Stress, WidgetSize.Small))
        assertEquals("WeeklyMileageWidgetReceiver", WidgetVariants.receiverName(WidgetKind.WeeklyMileage, WidgetSize.Wide))
        assertEquals("WeeklyMileageSmallWidgetReceiver", WidgetVariants.receiverName(WidgetKind.WeeklyMileage, WidgetSize.Small))
        assertEquals("Overnight HRV (2x2)", WidgetVariants.pickerLabel(WidgetKind.Hrv, WidgetSize.Small))
        assertEquals(WidgetKind.HeartRate, WidgetVariants.kindOf("app.cairn.widget.HeartRateWideWidgetReceiver"))
        assertEquals(WidgetKind.WeeklyMileage, WidgetVariants.kindOf("WeeklyMileageLargeWidgetReceiver"))
        assertEquals(WidgetSize.Large, WidgetVariants.sizeOf("StressLargeWidgetReceiver"))
        // The tap default of every new widget is COROS.
        for (k in kinds) assertEquals(TapTarget.CorosHome, TapTargets.default(k))
        assertTrue(PinnableWidget.entries.map { it.kind }.containsAll(kinds))
    }

    // ---- goal ring configuration ----

    @Test fun goalRoundTripsPerWidgetWithItsUnit() {
        val cfg = WidgetConfig(style = WidgetStyle.MileageGoal, weeklyGoal = WeeklyGoal(25, DistanceUnit.Mi), compactFooter = true)
        val back = WidgetConfig.decode(cfg.encode(), WidgetKind.WeeklyMileage)
        assertEquals(cfg, back)
        assertEquals(WeeklyGoal(30, DistanceUnit.Km), WidgetConfig.decode(WidgetConfig(weeklyGoal = WeeklyGoal(30, DistanceUnit.Km)).encode(), WidgetKind.WeeklyMileage).weeklyGoal)
        assertNull(WidgetConfig.decode(WidgetConfig().encode(), WidgetKind.WeeklyMileage).weeklyGoal)
        assertNull("garbage", WidgetConfig.decode("goal=abc;goal2=3", WidgetKind.WeeklyMileage).weeklyGoal)
        assertNull("zero", WidgetConfig.decode("goal=0km", WidgetKind.WeeklyMileage).weeklyGoal)
        assertNull("too big", WidgetConfig.decode("goal=999km", WidgetKind.WeeklyMileage).weeklyGoal)
        assertNull("no unit", WidgetConfig.decode("goal=30", WidgetKind.WeeklyMileage).weeklyGoal)
        // Resolved only for the widget that uses it.
        assertEquals(WeeklyGoal(30, DistanceUnit.Km), WidgetConfig(weeklyGoal = WeeklyGoal(30, DistanceUnit.Km)).resolve(WidgetKind.WeeklyMileage, AppAppearance()).weeklyGoal)
        assertNull(WidgetConfig(weeklyGoal = WeeklyGoal(30, DistanceUnit.Km)).resolve(WidgetKind.Hrv, AppAppearance()).weeklyGoal)
        // The style of another kind is ignored.
        assertEquals(WidgetStyle.HrvRange, WidgetConfig(style = WidgetStyle.StressGauge).resolve(WidgetKind.Hrv, AppAppearance()).style)
    }

    @Test fun goalDefaultsAndUnitConversion() {
        assertEquals(WeeklyGoal(30, DistanceUnit.Km), WeeklyGoal.default(DistanceUnit.Km))
        assertEquals(WeeklyGoal(20, DistanceUnit.Mi), WeeklyGoal.default(DistanceUnit.Mi))
        assertEquals(30000.0, WeeklyGoal(30, DistanceUnit.Km).meters, 0.001)
        assertEquals(32186.88, WeeklyGoal(20, DistanceUnit.Mi).meters, 0.01)
        assertEquals(19, WeeklyGoal(30, DistanceUnit.Km).inUnit(DistanceUnit.Mi))
        assertEquals(32, WeeklyGoal(20, DistanceUnit.Mi).inUnit(DistanceUnit.Km))
        assertEquals(30, WeeklyGoal(30, DistanceUnit.Km).inUnit(DistanceUnit.Km))
    }

    @Test fun newPartsAreTogglesOfTheirOwnKindOnly() {
        for (k in kinds) assertTrue(Part.forKind(k).isNotEmpty())
        val r = WidgetConfig(hidden = setOf(Part.NormalRange, Part.SevenDayAvg)).resolve(WidgetKind.Hrv, AppAppearance())
        assertEquals(setOf(Part.NormalRange), r.hidden)
        assertTrue(WidgetConfig.decode(WidgetConfig(hidden = setOf(Part.VsLastWeek)).encode(), WidgetKind.WeeklyMileage).hidden == setOf(Part.VsLastWeek))
    }

    // ---- contrast, every style ----

    @Test fun semanticColoursAndPillTextPassContrastInEveryStyle() {
        val palettes = ThemeId.entries.flatMap { t -> listOf(false, true).flatMap { dark -> listOf(Palettes.fixed(t, dark), Palettes.fixed(t, dark, AccentOption.Coral)) } } +
            listOf(false, true).map { Palettes.onWallpaper(it) }
        for (p in palettes) {
            val bgs = p.textStops + p.tile
            for (t in Tone.entries) {
                val c = p.tone(t)
                assertTrue("tone $t on card ${Contrast.minRatio(c, bgs)}", Contrast.minRatio(c, bgs) >= 3.0)
                assertTrue("pill text on $t ${Contrast.ratio(Contrast.bestInk(listOf(c)), c)}", Contrast.ratio(Contrast.bestInk(listOf(c)), c) >= 4.5)
            }
            assertTrue("heart rose ${Contrast.minRatio(p.stageRem, bgs)}", Contrast.minRatio(p.stageRem, bgs) >= 3.0)
            assertTrue("labels", Contrast.minRatio(p.inkSub, bgs) >= 4.5)
            assertTrue("numbers", Contrast.minRatio(p.ink, bgs) >= 4.5)
            // The five distinct stress and HRV tones must be told apart without colour: words always accompany them (tested above).
            assertTrue(Tone.entries.map { p.tone(it) }.toSet().size >= 4)
        }
    }
}
