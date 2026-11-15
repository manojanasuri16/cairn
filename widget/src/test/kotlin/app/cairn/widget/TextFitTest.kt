package app.cairn.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {
    private val longest = listOf("1:58:07", "4:09:57", "12.34 km", "10:45 /mi", "152 bpm", "1,234 kcal", "7h 41m")
    private val widths = listOf(110f, 130f, 160f, 200f, 250f, 300f, 360f)
    private val scales = listOf(1f, 1.3f, 2f)

    private fun inner(w: Float, h: Float) = w - 2f * Layouts.pad(app.cairn.core.designsystem.SizeClass.of(w, h), h)

    @Test fun widthGrowsWithTextSizeAndFontScale() {
        assertTrue(TextFit.widthDp("4:09:57", 16f, 1f) > TextFit.widthDp("4:09:57", 12f, 1f))
        assertTrue(TextFit.widthDp("4:09:57", 14f, 1.3f) > TextFit.widthDp("4:09:57", 14f, 1f))
        assertEquals(TextFit.widthDp("4:09:57", 14f, 1.5f), TextFit.widthDp("4:09:57", 14f, 2f), 0.01f) // capped like Txt
    }

    @Test fun fitSpShrinksBeforeGivingUp() {
        val sp = TextFit.fitSp("1,234 kcal", 16f, 8f, 70f, 1f)
        assertTrue(sp < 16f && TextFit.fits("1,234 kcal", sp, 1f, 70f))
    }

    @Test fun tileRowsFitTheirTextAndSpanTheFullWidth() {
        for (style in listOf(WidgetStyle.ActivityGrid, WidgetStyle.FitnessGrid)) for (w in widths) for (h in listOf(75f, 110f, 160f, 300f)) for (s in scales) {
            val p = Layouts.plan(style, w, h, s, values = longest)
            val n = p.count("t") + p.count("p")
            val inner = inner(w, h)
            val shown = longest.take(n)
            assertTrue("$style ${w}x$h x$s: ${p.perRow} cols at ${p.heroSp}sp do not fit $shown", Layouts.rowsFit(shown, p.perRow, p.heroSp, inner, s))
            // Legible: renders at 12 dp or more after the font scale.
            assertTrue("$style value ${p.heroSp}sp x$s", p.heroSp * TextFit.effectiveScale(s) >= 11.9f)
            // Edge alignment: in every row the tile widths plus the gaps equal the content width, and no tile is narrower than its text.
            var idx = 0
            for (cnt in app.cairn.core.designsystem.FitMath.rows(n, p.perRow)) {
                val line = shown.subList(idx, idx + cnt); idx += cnt
                val needs = line.map { TextFit.widthDp(it, p.heroSp, s) + Layouts.TILE_PAD }
                val widths = app.cairn.core.designsystem.FitMath.balancedWidths(needs, inner, 4f)
                assertEquals("$style ${w}x$h row ends short", inner, widths.sum() + 4f * (cnt - 1), 0.01f)
                needs.zip(widths).forEach { (need, got) -> assertTrue(got >= need) }
            }
        }
    }

    @Test fun fourCompactTilesFillAWideShortStrip() {
        val values = listOf("5.6 km", "55m", "9:50 /km", "151 bpm")
        val p = Layouts.plan(WidgetStyle.ActivityGrid, 272f, 75f, 1f, values = values)
        assertEquals(WidgetStyle.ActivityGrid, p.style)
        assertEquals(4, p.count("t")); assertEquals(4, p.perRow)
        assertTrue(p.heroSp >= TextFit.minSp(1f))
        // Only when even the floor size does not fit are tiles dropped, lowest priority first.
        val narrow = Layouts.plan(WidgetStyle.ActivityGrid, 250f, 75f, 2f, values = values)
        assertTrue(narrow.count("t") in 1..4)
        assertEquals("Dist", Layouts.shortLabel("Distance")); assertEquals("HR", Layouts.shortLabel("Heart rate")); assertEquals("HM", Layouts.shortLabel("Half marathon"))
    }

    @Test fun wideCellsFallBackToTwoColumnsWhenFourWouldCut() {
        val p = Layouts.plan(WidgetStyle.ActivityGrid, 300f, 160f, 1f, values = listOf("5.6 km", "10:45 /mi", "1h 52m", "152 bpm"))
        assertTrue(p.perRow == 2 || p.perRow == 4)
        assertTrue(Layouts.rowsFit(listOf("5.6 km", "10:45 /mi", "1h 52m", "152 bpm"), p.perRow, p.heroSp, inner(300f, 160f), 1f))
        val race = Layouts.plan(WidgetStyle.FitnessGrid, 300f, 160f, 1f, values = listOf("25:06", "52:33", "1:58:07", "4:09:57"))
        assertTrue(race.perRow == 4 || race.perRow == 2)
        assertTrue(Layouts.rowsFit(listOf("25:06", "52:33", "1:58:07", "4:09:57"), race.perRow, race.heroSp, inner(300f, 160f), 1f))
    }

    @Test fun textHeroesAlwaysFitTheirColumn() {
        val cases = mapOf(
            WidgetStyle.TodayStack to "12,345", WidgetStyle.RecoveryPill to "100%", WidgetStyle.ActivityHero to "12.34 km",
            WidgetStyle.LoadBars to "1.12", WidgetStyle.LoadTrend to "1.12", WidgetStyle.SleepBar to "7h 41m",
            WidgetStyle.SleepTimeline to "11h 41m", WidgetStyle.FitnessVo2 to "52",
        )
        for ((style, text) in cases) for (w in widths) for (h in listOf(110f, 160f, 300f)) for (s in scales) {
            val p = Layouts.plan(style, w, h, s, heroText = text)
            assertTrue("$style ${w}x$h x$s '$text' at ${p.heroSp}sp in ${p.heroAvailDp}", TextFit.fits(text, p.heroSp, s, p.heroAvailDp, Layouts.HERO_CAP))
            assertTrue(p.heroSp * TextFit.effectiveScale(s, Layouts.HERO_CAP) >= 13.9f)
        }
    }

    @Test fun oneLinerKeepsTheFirstValueWhole() {
        for (w in widths) for (s in scales) {
            val text = Layouts.activityMain(listOf("12.34 km", "1h 52m"), w - 16f, s)
            assertTrue(text.startsWith("12.34 km"))
        }
        assertEquals("12.34 km · 1h 52m", Layouts.activityMain(listOf("12.34 km", "1h 52m"), 360f, 1f))
    }

    @Test fun sleepLegendWrapsInsteadOfCutting() {
        val entries = listOf("1h 39m", "3h 44m", "1h 40m", "21m")
        for (w in widths) for (s in scales) {
            val lines = Layouts.legendLines(entries, w - 24f, s)
            assertEquals(entries, lines.flatten())
            for (line in lines.filter { it.size > 1 }) {
                val used = line.sumOf { (TextFit.widthDp(it, 10f, s) + 13f).toDouble() } + 6f * (line.size - 1)
                assertTrue("$w x$s $line", used <= w - 24f + 0.5f)
            }
        }
        // A narrow cell needs more lines than a wide one, and the plan budgets for them.
        assertTrue(Layouts.legendLines(entries, 90f, 1f).size > Layouts.legendLines(entries, 300f, 1f).size)
        val narrow = Layouts.plan(WidgetStyle.SleepBar, 160f, 200f, 1f, heroText = "7h 41m", values = entries)
        assertTrue(narrow.usedDp <= narrow.availDp + 0.5f)
    }

    @Test fun fitnessGridUsesTallCells() {
        val short = Layouts.plan(WidgetStyle.FitnessGrid, 300f, 150f, 1f, values = listOf("25:06", "52:33", "1:58:07", "4:09:57"))
        val tall = Layouts.plan(WidgetStyle.FitnessGrid, 300f, 320f, 1f, values = listOf("25:06", "52:33", "1:58:07", "4:09:57"))
        assertTrue(tall.has("thr") || tall.tileDp > short.tileDp)
        assertTrue(tall.usedDp >= tall.availDp * 0.8f)
    }
}
