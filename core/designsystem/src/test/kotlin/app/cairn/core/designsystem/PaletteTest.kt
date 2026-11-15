package app.cairn.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun styles(): List<Palette> = ThemeId.entries.flatMap { t -> listOf(false, true).map { Palettes.fixed(t, it) } } + listOf(false, true).map { Palettes.onWallpaper(it) }
    private fun heroes(): List<Palette> = Accent.entries.flatMap { a -> listOf(false, true).map { AppColors.hero(a, it) } }
    private fun all() = styles() + heroes()

    @Test fun contrastFunctionMatchesWcagReferenceValues() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001)
        assertEquals(4.54, Contrast.ratio(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test fun widgetTextReachesAaWhereTextSits() {
        for (p in all()) for (bg in p.textStops + p.tile) {
            assertTrue("ink ${Contrast.ratio(p.ink, bg)} dark=${p.dark}", Contrast.ratio(p.ink, bg) >= 4.5)
            assertTrue("inkSub ${Contrast.ratio(p.inkSub, bg)}", Contrast.ratio(p.inkSub, bg) >= 4.5)
            assertTrue("warnText", Contrast.ratio(p.warnText, bg) >= 4.5)
        }
    }

    @Test fun cornerStillReachesLargeTextContrast() {
        for (p in all()) assertTrue("${Contrast.ratio(p.ink, p.end)}", Contrast.ratio(p.ink, p.end) >= 3.0)
    }

    @Test fun graphicColoursReachThreeToOneOnTheTextArea() {
        for (p in all()) for (bg in p.textStops + p.tile) {
            for ((name, c) in listOf("accent" to p.accent, "good" to p.good, "warn" to p.warn, "bad" to p.bad, "cool" to p.cool,
                "deep" to p.stageDeep, "light" to p.stageLight, "rem" to p.stageRem, "awake" to p.stageAwake)) {
                assertTrue("$name ${Contrast.ratio(c, bg)} dark=${p.dark}", Contrast.ratio(c, bg) >= 3.0)
            }
        }
    }

    @Test fun accentOverrideKeepsContrast() {
        for (a in AccentOption.entries) for (t in ThemeId.entries) for (dark in listOf(false, true)) {
            val p = Palettes.fixed(t, dark, a)
            for (bg in p.textStops) assertTrue("$a $t $dark", Contrast.ratio(p.accent, bg) >= 3.0)
            assertTrue(Contrast.ratio(p.onAccent, p.accent) >= 4.5)
        }
    }

    @Test fun semanticColoursFollowLevels() {
        val p = Palettes.fixed(ThemeId.Ocean, false)
        assertEquals(p.good, p.recovery(85)); assertEquals(p.warn, p.recovery(60)); assertEquals(p.bad, p.recovery(20))
        assertEquals(p.cool, p.load(0.5)); assertEquals(p.good, p.load(1.0)); assertEquals(p.warn, p.load(1.4)); assertEquals(p.bad, p.load(1.8))
        assertEquals(p.accent, p.load(null))
    }

    @Test fun stageHuesAreDistinct() {
        for (p in all()) assertEquals(4, setOf(p.stageDeep, p.stageLight, p.stageRem, p.stageAwake).size)
        for (dark in listOf(false, true)) { val c = AppColors.card(dark); assertEquals(4, setOf(c.deep, c.light, c.rem, c.awake).size) }
    }

    @Test fun appSurfacesAreNeutralForEveryAccentAndTextIsAa() {
        for (dark in listOf(false, true)) {
            val bgs = Accent.entries.map { AppColors.roles(it, dark).background }.toSet()
            assertEquals("surfaces must not depend on the accent", 1, bgs.size)
            for (a in Accent.entries) {
                val r = AppColors.roles(a, dark)
                for (bg in listOf(r.background, r.card)) {
                    assertTrue("$a $dark onSurface", Contrast.ratio(r.onSurface, bg) >= 4.5)
                    assertTrue("$a $dark variant ${Contrast.ratio(r.onSurfaceVariant, bg)}", Contrast.ratio(r.onSurfaceVariant, bg) >= 4.5)
                    assertTrue("$a $dark primary ${Contrast.ratio(r.primary, bg)}", Contrast.ratio(r.primary, bg) >= 3.0)
                }
                assertTrue("$a $dark onPrimary", Contrast.ratio(r.onPrimary, r.primary) >= 4.5)
                assertTrue("$a $dark onContainer", Contrast.ratio(r.onContainer, r.container) >= 4.5)
                val c = AppColors.card(dark)
                for (col in listOf(c.good, c.warn, c.bad, c.cool, c.deep, c.light, c.rem, c.awake)) assertTrue("$dark card colour ${Contrast.ratio(col, r.card)}", Contrast.ratio(col, r.card) >= 3.0)
            }
        }
    }

    @Test fun darkNeutralsMatchTheReferenceApp() {
        val r = AppColors.roles(Accent.DUSK, true)
        assertEquals(0xFF0F1012.toInt(), r.background); assertEquals(0xFF191A1E.toInt(), r.card)
        val l = AppColors.roles(Accent.DUSK, false)
        assertEquals(0xFFF7F7F5.toInt(), l.background); assertEquals(0xFFFFFFFF.toInt(), l.card)
        assertEquals(Accent.DUSK, Accent.DEFAULT); assertEquals(8, Accent.entries.size)
    }

    @Test fun duskKeepsItsExactStartAndCentre() {
        val p = Palettes.fixed(ThemeId.Dusk, false)
        assertEquals(0xFF232A6E.toInt(), p.start); assertEquals(0xFF4A52B8.toInt(), p.center)
        assertEquals(ThemeId.Clean, ThemeId.Default)
    }

    @Test fun semanticPillsKeepReadableText() {
        for (p in all()) for (c in listOf(p.good, p.warn, p.bad, p.cool)) {
            val ink = Contrast.bestInk(listOf(c))
            assertTrue("pill ${Contrast.ratio(ink, c)} dark=${p.dark}", Contrast.ratio(ink, c) >= 4.5)
        }
    }

    @Test fun everyStyleLooksDifferent() {
        assertEquals(ThemeId.entries.size, ThemeId.entries.map { Palettes.fixed(it, false).let { p -> p.start to p.end } }.toSet().size)
    }

    @Test fun sizeClassesAndHeroTokens() {
        assertEquals(SizeClass.Small, SizeClass.of(110f, 110f))
        assertEquals(SizeClass.Medium, SizeClass.of(160f, 120f))
        assertEquals(SizeClass.Large, SizeClass.of(250f, 160f))
        assertEquals(SizeClass.XLarge, SizeClass.of(320f, 320f))
        assertTrue(SizeClass.entries.zipWithNext().all { (a, b) -> WidgetTokens.heroSp(a) < WidgetTokens.heroSp(b) })
    }
}
