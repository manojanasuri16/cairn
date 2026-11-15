package app.cairn.core.designsystem

import kotlin.math.pow

/**
 * Pure colour tokens shared by the app and the widgets (plain ARGB ints, no Android or Compose types, so
 * contrast is unit-tested on the JVM). Every text colour is guaranteed WCAG AA (4.5:1) against every
 * background stop it can sit on, every graphic colour 3:1.
 */
enum class ThemeId(val label: String) {
    Clean("Clean"), Dusk("Dusk"), Dawn("Dawn"), Forest("Forest"), Midnight("Midnight"), Sunrise("Sunrise"),
    Ocean("Ocean"), Violet("Violet"), Berry("Berry"), Minimal("Minimal");

    companion object {
        val Default = Clean
        fun parse(raw: String?): ThemeId? = entries.firstOrNull { it.name == raw }
    }
}

/** Optional accent colour that replaces the theme's accent for rings and highlights. */
enum class AccentOption(val label: String, val light: Int, val dark: Int) {
    Theme("Theme", 0, 0),
    Coral("Coral", 0xFFC2410C.toInt(), 0xFFFF8A65.toInt()),
    Amber("Amber", 0xFF92400E.toInt(), 0xFFFBBF24.toInt()),
    Lime("Lime", 0xFF3F6212.toInt(), 0xFFBEF264.toInt()),
    Sky("Sky", 0xFF075985.toInt(), 0xFF7DD3FC.toInt()),
    Violet("Violet", 0xFF5B21B6.toInt(), 0xFFC4B5FD.toInt()),
    Pink("Pink", 0xFF9D174D.toInt(), 0xFFF9A8D4.toInt());

    companion object { fun parse(raw: String?): AccentOption = entries.firstOrNull { it.name == raw } ?: Theme }
}

object Contrast {
    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    /** WCAG contrast ratio, 1..21. Alpha is ignored; blend first. */
    fun ratio(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** [fg] at [alpha] over opaque [bg]. */
    fun blend(fg: Int, bg: Int, alpha: Float): Int {
        fun ch(shift: Int): Int {
            val f = (fg shr shift) and 0xFF; val b = (bg shr shift) and 0xFF
            return (f * alpha + b * (1 - alpha)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun withAlpha(argb: Int, alpha: Float): Int = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)

    fun minRatio(c: Int, bgs: List<Int>): Double = bgs.minOf { ratio(c, it) }

    const val NEAR_BLACK = 0xFF101418.toInt()
    const val NEAR_WHITE = 0xFFFFFFFF.toInt()

    /** Whichever of near-black / white reads better on all [bgs]. */
    fun bestInk(bgs: List<Int>): Int =
        if (minRatio(NEAR_BLACK, bgs) >= minRatio(NEAR_WHITE, bgs)) NEAR_BLACK else NEAR_WHITE

    /**
     * Moves [c] towards black (on light backgrounds) or white (on dark ones) until it reaches [min] on every
     * background in [bgs]. Keeps the hue as far as possible; guaranteed to terminate at pure black/white.
     */
    fun ensure(c: Int, bgs: List<Int>, min: Double): Int {
        if (minRatio(c, bgs) >= min) return c
        val target = if (bestInk(bgs) == NEAR_BLACK) 0xFF000000.toInt() else NEAR_WHITE
        var step = 1
        while (step <= 20) {
            val t = blend(target, c, step / 20f)
            if (minRatio(t, bgs) >= min) return t
            step++
        }
        return target
    }
}

/** Resolved colours for one theme in one brightness. All fields are opaque ARGB. */
data class Palette(
    val dark: Boolean,
    val start: Int,
    val center: Int,
    val end: Int,
    val ink: Int,
    val inkSub: Int,
    val accent: Int,
    val onAccent: Int,
    /** Track behind rings and bars; decorative. */
    val track: Int,
    /** Tinted tile / chip fill; text on it is [ink]. */
    val tile: Int,
    val good: Int,
    val warn: Int,
    val bad: Int,
    val cool: Int,
    val stageDeep: Int,
    val stageLight: Int,
    val stageRem: Int,
    val stageAwake: Int,
) {
    /** Every colour of the gradient. */
    val stops: List<Int> get() = listOf(start, center, end)

    /**
     * Where text and thin graphics actually sit. The far end corner of a 315 degree gradient only holds the card's corner,
     * so text is checked at the start, the centre and halfway to the end; the corner itself must still reach 3:1 for large text.
     */
    val textStops: List<Int> get() = listOf(start, center, Contrast.blend(end, center, 0.5f))

    /** Recovery colour by level (same 80 / 50 thresholds as the labels). */
    fun recovery(percent: Int): Int = when {
        percent >= 80 -> good
        percent >= 50 -> warn
        else -> bad
    }

    /** Training load ratio colour by range (same bands as the labels). Null ratio uses the accent. */
    fun load(ratio: Double?): Int = when {
        ratio == null -> accent
        ratio < 0.8 -> cool
        ratio <= 1.3 -> good
        ratio <= 1.5 -> warn
        else -> bad
    }

    /** Stale footer / warning colour, readable as text on the card. */
    val stale: Int get() = warnText

    /** [warn] pushed to 4.5:1 so it can be used for text. */
    val warnText: Int get() = Contrast.ensure(warn, textStops + tile, 4.5)
}

object Palettes {
    private class Spec(
        val start: Long, val center: Long, val end: Long, val ink: Long, val soft: Long?, val accent: Long, val track: Long,
        val good: Long, val bad: Long, val light: Boolean = false,
    )

    private val dusk = Spec(0xFF232A6E, 0xFF4A52B8, 0xFFC07A9A, 0xFFFFFFFF, null, 0xFFFFD3E2, 0x2EFFFFFF, 0xFF9FE6C8, 0xFFFF9A87)
    private val dawn = Spec(0xFF1C5547, 0xFF2E7E68, 0xFFC98A5F, 0xFFFFFFFF, null, 0xFFFFD9B8, 0x2EFFFFFF, 0xFF8BE3BD, 0xFFFF9A87)
    private val forest = Spec(0xFF2A6252, 0xFF1E4A3E, 0xFF12302A, 0xFFEFF8F3, null, 0xFF8FE0C4, 0x29EFF8F3, 0xFF6FD3A6, 0xFFFF8C7C)
    private val midnight = Spec(0xFF1C2330, 0xFF131924, 0xFF0A0D12, 0xFFF2F5F8, 0xFF9AA5B1, 0xFF7FD3B9, 0x24F2F5F8, 0xFF4CC38F, 0xFFF07C72)
    private val sunrise = Spec(0xFFFFF4EA, 0xFFFFE6D2, 0xFFFFD2B0, 0xFF3A1F10, 0xFF8A5A3C, 0xFFC2622F, 0x29C2622F, 0xFF3BA57B, 0xFFE0675C, light = true)
    private val ocean = Spec(0xFF0E2F55, 0xFF1F5F9E, 0xFF3D8FD0, 0xFFFFFFFF, null, 0xFFBFE0FF, 0x2EFFFFFF, 0xFF9FE6C8, 0xFFFF9A87)
    private val violet = Spec(0xFF2A1659, 0xFF5B3AAE, 0xFFA06BD0, 0xFFFFFFFF, null, 0xFFE4D0FF, 0x2EFFFFFF, 0xFF9FE6C8, 0xFFFF9A87)
    private val berry = Spec(0xFF4A0F3A, 0xFF8E2A6A, 0xFFD2588F, 0xFFFFFFFF, null, 0xFFFFD0E4, 0x2EFFFFFF, 0xFF9FE6C8, 0xFFFFB09E)
    // Clean: a calm tonal card (Google Health / Apple Health): near-white or true-dark surface, no gradient, accents carry the colour.
    private val cleanLight = Spec(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F7F5, 0xFF1A1B1E, 0xFF5E6068, 0xFF4C55C7, 0x1A1A1B1E, 0xFF2F8F68, 0xFFC9473C, light = true)
    private val cleanDark = Spec(0xFF191A1E, 0xFF191A1E, 0xFF16171A, 0xFFE4E4E8, 0xFFAAADB6, 0xFFB4BAFF, 0x26E4E4E8, 0xFF4CC38F, 0xFFF07C72)
    private val minimalLight = Spec(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF6F5F1, 0xFF181C1B, 0xFF66706B, 0xFF2B7A67, 0x1F2B7A67, 0xFF3BA57B, 0xFFE0675C, light = true)
    private val minimalDark = Spec(0xFF1B2120, 0xFF181D1C, 0xFF141918, 0xFFE7ECE9, 0xFF9EA8A4, 0xFF7FD3B9, 0x297FD3B9, 0xFF4CC38F, 0xFFF07C72)

    private fun spec(theme: ThemeId, dark: Boolean) = when (theme) {
        ThemeId.Dusk -> dusk; ThemeId.Dawn -> dawn; ThemeId.Forest -> forest; ThemeId.Midnight -> midnight; ThemeId.Sunrise -> sunrise
        ThemeId.Ocean -> ocean; ThemeId.Violet -> violet; ThemeId.Berry -> berry
        ThemeId.Minimal -> if (dark) minimalDark else minimalLight
        ThemeId.Clean -> if (dark) cleanDark else cleanLight
    }

    /** A widget style. Only Minimal follows light/dark; the others keep their look. Gradient ends are fitted so text keeps AA. */
    fun fixed(theme: ThemeId, dark: Boolean, accent: AccentOption = AccentOption.Theme): Palette {
        val s = spec(theme, dark)
        return build(s.start.toInt(), s.center.toInt(), s.end.toInt(), s.ink.toInt(), s.soft?.toInt(), s.accent.toInt(), s.track.toInt(), s.good.toInt(), s.bad.toInt(), dark, accent)
    }

    /** Hero gradient of an app accent: white text on a two-stop gradient (centre = midpoint), fitted for AA. */
    fun hero(startIn: Int, endIn: Int, dark: Boolean, accentTint: Int): Palette {
        var start = startIn; var end = endIn
        var n = 0
        // Darken both stops a little until white text reaches 4.5:1 on the start and the midpoint.
        while (n < 12 && Contrast.minRatio(0xFFFFFFFF.toInt(), listOf(start, Contrast.blend(end, start, 0.5f))) < 4.5) {
            start = Contrast.blend(0xFF000000.toInt(), start, 0.06f); end = Contrast.blend(0xFF000000.toInt(), end, 0.06f); n++
        }
        return build(start, Contrast.blend(end, start, 0.5f), end, 0xFFFFFFFF.toInt(), null, accentTint, 0x2EFFFFFF, 0xFF9FE6C8.toInt(), 0xFFFF9A87.toInt(), dark, AccentOption.Theme)
    }

    private fun build(
        start: Int, center: Int, endIn: Int, inkIn: Int, softIn: Int?, accentIn: Int, trackIn: Int, goodIn: Int, badIn: Int,
        dark: Boolean, accentOption: AccentOption,
    ): Palette {
        // Fit only the far end: darken it a little until text reaches 4.5 at the start, centre and halfway to the end.
        var end = endIn
        var steps = 0
        fun mid() = Contrast.blend(end, center, 0.5f)
        fun tileNow() = Contrast.blend(inkIn, mid(), 0.12f)
        while (steps < 12 && Contrast.minRatio(inkIn, listOf(start, center, mid(), tileNow())) < 4.5) {
            end = Contrast.blend(0xFF000000.toInt(), end, 0.08f); steps++
        }
        val textBgs = listOf(start, center, Contrast.blend(end, center, 0.5f))
        val midAll = Contrast.blend(end, center, 0.5f)
        val tile = Contrast.blend(inkIn, midAll, 0.12f)
        val tiles = textBgs + tile
        val sub = softIn?.takeIf { Contrast.minRatio(it, tiles) >= 4.5 } ?: Contrast.ensure(Contrast.blend(inkIn, midAll, 0.8f), tiles, 4.5)
        val light = Contrast.bestInk(textBgs) == Contrast.NEAR_BLACK
        val accentBase = if (accentOption != AccentOption.Theme) (if (light) accentOption.light else accentOption.dark) else accentIn
        // Semantic colours: 3:1 on the card, and a shade at which black or white text reaches 4.5:1 inside a pill.
        fun g(c0: Int): Int {
            var c = Contrast.ensure(c0, tiles, 3.0)
            var n = 0
            while (n < 12 && maxOf(Contrast.ratio(c, Contrast.NEAR_BLACK), Contrast.ratio(c, Contrast.NEAR_WHITE)) < 4.5) {
                c = Contrast.blend(if (light) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), c, 0.06f); n++
            }
            return c
        }
        val accent = g(accentBase)
        return Palette(
            dark = dark, start = start, center = center, end = end, ink = inkIn, inkSub = sub,
            accent = accent, onAccent = Contrast.bestInk(listOf(accent)),
            track = trackIn, tile = tile,
            good = g(goodIn), warn = g(if (light) 0xFFB7791F.toInt() else 0xFFFFD27A.toInt()), bad = g(badIn),
            cool = g(if (light) 0xFF1F6FB2.toInt() else 0xFF9CCBFF.toInt()),
            stageDeep = g(if (light) 0xFF4C55C7.toInt() else 0xFFA9B1FF.toInt()),
            stageLight = g(if (light) 0xFF1F6FB2.toInt() else 0xFF8ED1FC.toInt()),
            stageRem = g(if (light) 0xFFAE426D.toInt() else 0xFFF2A7D0.toInt()),
            stageAwake = g(if (light) 0xFFB8641A.toInt() else 0xFFFFC58A.toInt()),
        )
    }

    /** What to draw on a transparent widget: no background, ink sized for a typical wallpaper of that brightness. */
    fun onWallpaper(dark: Boolean, accent: AccentOption = AccentOption.Theme, accentHint: Int = if (dark) 0xFFA5B4FC.toInt() else 0xFF3730A3.toInt()): Palette {
        val base = if (dark) 0xFF1B1B1F.toInt() else 0xFFF4F4F8.toInt()
        val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF181C1B.toInt()
        return build(base, base, base, ink, null, accentHint, if (dark) 0x33FFFFFF else 0x22000000, 0xFF4CC38F.toInt(), 0xFFF07C72.toInt(), dark, accent)
    }
}

/** Typography and spacing tokens shared by every widget. 4 dp grid. */
object WidgetTokens {
    const val GRID = 4
    const val PAD_COMPACT = 12
    const val PAD_REGULAR = 16
    const val PAD_LARGE = 20
    const val GAP = 8
    const val MIN_HERO_SP = 22f
    const val MIN_RING_NUMERAL_SP = 14f

    /** Hero numeral size per size class (sp at font scale 1). */
    fun heroSp(sizeClass: SizeClass): Float = when (sizeClass) {
        SizeClass.Small -> 28f
        SizeClass.Medium -> 34f
        SizeClass.Large -> 44f
        SizeClass.XLarge -> 56f
    }

    const val TITLE_SP = 14f
    const val BODY_SP = 13f
    const val CAPTION_SP = 11f
    const val MICRO_SP = 10f
}

enum class SizeClass {
    Small, Medium, Large, XLarge;

    companion object {
        fun of(widthDp: Float, heightDp: Float): SizeClass = when {
            widthDp >= 300f && heightDp >= 200f -> XLarge
            widthDp >= 220f && heightDp >= 150f -> Large
            widthDp >= 140f && heightDp >= 100f -> Medium
            else -> Small
        }
    }
}

