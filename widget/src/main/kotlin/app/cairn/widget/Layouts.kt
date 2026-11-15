package app.cairn.widget

import app.cairn.core.designsystem.FitMath
import app.cairn.core.designsystem.SizeClass
import app.cairn.core.designsystem.WidgetTokens
import kotlin.math.ceil

/**
 * Pure layout decisions for every style variant, from the exact size Glance reports (SizeMode.Exact) and the
 * system font scale. Nothing here touches Android, so each variant x size class is unit-tested.
 *
 * Rules every plan follows: the hero is always shown and never smaller than [WidgetTokens.MIN_HERO_SP]
 * (ring numerals: [WidgetTokens.MIN_RING_NUMERAL_SP]); secondary blocks are added in priority order only while
 * they fit; larger cells scale the hero up within its size class and add the next blocks.
 */
object Layouts {
    /** Text is allowed to grow with the system font up to this factor; beyond it the sp is shrunk so layouts hold. */
    const val TEXT_CAP = 1.5f
    const val HERO_CAP = 1.3f

    fun ts(fontScale: Float): Float = fontScale.coerceIn(1f, TEXT_CAP)

    /** Very short cells (a 4x1 strip) get tighter padding so a single line still fits with the footer. */
    fun pad(sizeClass: SizeClass, h: Float): Int = if (h < 64f) 6 else if (h < 90f) 8 else pad(sizeClass)

    /** One padding for every regular cell, so layout thresholds do not shift with the size class. */
    fun pad(sizeClass: SizeClass): Int = WidgetTokens.PAD_COMPACT + 2

    /** Height the footer takes: 11sp text (capped) plus its padding. */
    fun footerDp(fontScale: Float): Float = 10f + 11f * 1.25f * ts(fontScale)

    fun availDp(w: Float, h: Float, fontScale: Float): Float {
        val p = pad(SizeClass.of(w, h), h)
        return (h - 2 * p - footerDp(fontScale)).coerceAtLeast(0f)
    }

    fun heroSp(sizeClass: SizeClass, fontScale: Float): Float =
        SizeRules.cappedSp(WidgetTokens.heroSp(sizeClass), fontScale, HERO_CAP).coerceAtLeast(WidgetTokens.MIN_HERO_SP)

    /** Height of a hero line at [sp] when rendered at [fontScale]. */
    fun heroLineDp(sp: Float, fontScale: Float): Float = sp * 1.2f * fontScale.coerceAtMost(HERO_CAP).coerceAtLeast(1f)

    data class Block(val id: String, val dp: Float)

    /** Greedy in priority order; stops at the first block that does not fit so lower-priority content never jumps ahead. */
    fun budget(avail: Float, blocks: List<Block>): Set<String> {
        var left = avail
        val out = LinkedHashSet<String>()
        for (b in blocks) { if (b.dp > left) break; left -= b.dp; out += b.id }
        return out
    }

    /**
     * What to draw. [heroDp] is the ring / gauge / dial / bar-chart size (0 for text heroes), [heroSp] the hero numeral,
     * [blocks] the secondary content that fits, [perRow] columns for grid-like variants, [side] hero beside the rest.
     */
    data class Plan(
        val style: WidgetStyle,
        val sizeClass: SizeClass,
        val pad: Int,
        val side: Boolean,
        val heroDp: Int,
        val heroSp: Float,
        val blocks: Set<String>,
        val perRow: Int,
        val usedDp: Float,
        val availDp: Float,
        /** Width the text hero (or tile value) was fitted to. */
        val heroAvailDp: Float = 0f,
        /** Height of one grid tile row (tiles stretch to use a tall cell). */
        val tileDp: Float = 0f,
    ) {
        fun has(id: String) = id in blocks
        fun count(prefix: String) = blocks.count { it.startsWith(prefix) }
    }

    /** Hero text size: the class size, shrunk (never below 14 dp effective) only as far as needed so the whole value fits [availW]. */
    fun fitHero(text: String, base: Float, availW: Float, fontScale: Float, maxLineDp: Float = Float.MAX_VALUE): Float {
        if (text.isEmpty()) return base
        val floor = minOf((14f / TextFit.effectiveScale(fontScale, HERO_CAP)).coerceAtLeast(10f), base)
        // Scales to the width: up to 60% above the class size in a wide cell, down to the floor in a narrow one.
        var sp = base * 1.6f
        while (sp > floor && (!TextFit.fits(text, sp, fontScale, availW, HERO_CAP) || heroLineDp(sp, fontScale) > maxLineDp)) sp -= 1f
        return maxOf(sp, floor)
    }

    /** One-liner text: as many of [parts] (joined by a dot) as fit [inner] dp at the smallest legible size; the first always stays whole. */
    fun activityMain(parts: List<String>, inner: Float, fontScale: Float): String {
        val clean = parts.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return "\u2013"
        for (k in clean.size downTo 1) {
            val text = clean.take(k).joinToString(" \u00b7 ")
            val floor = (14f / TextFit.effectiveScale(fontScale, HERO_CAP)).coerceAtLeast(10f)
            if (TextFit.fits(text, floor, fontScale, inner, HERO_CAP)) return text
        }
        return clean.first()
    }

    /** Shortened forms for metric row labels, used before a label is dropped. */
    fun shortLabel(label: String): String = when (label) {
        "Resting HR" -> "RHR"; "Recovery" -> "Rec"; "Calories" -> "Cal"; "Heart rate" -> "HR"; "Distance" -> "Dist"; "Half marathon" -> "HM"; "Marathon" -> "M"; else -> label
    }

    /** The label to draw in front of [value] on a row [availW] dp wide: full, else shortened, else null (the value alone is kept whole). */
    fun rowLabel(label: String, value: String, availW: Float, fontScale: Float, iconDp: Float = 20f): String? {
        val room = availW - iconDp - TextFit.widthDp(value, 13f, fontScale) - 8f
        return when {
            TextFit.fits(label, 12f, fontScale, room) -> label
            TextFit.fits(shortLabel(label), 12f, fontScale, room) -> shortLabel(label)
            else -> null
        }
    }

    const val TILE_PAD = 16f
    fun tileWidth(inner: Float, cols: Int): Float = (inner - 4f * (cols - 1)) / cols - TILE_PAD

    /** True when [values] laid out [cols] per row at [sp] each fit [inner] dp: every row's tile widths (text plus padding) and gaps sum to at most the width. */
    fun rowsFit(values: List<String>, cols: Int, sp: Float, inner: Float, fontScale: Float): Boolean {
        var idx = 0
        return FitMath.rows(values.size, cols).all { n ->
            val line = values.subList(idx, idx + n); idx += n
            line.sumOf { (TextFit.widthDp(it, sp, fontScale) + TILE_PAD).toDouble() } + 4f * (n - 1) <= inner
        }
    }

    /** Columns and value size for a tile grid: most columns whose rows fit every value, then 2, then 1, shrinking the value (to a legible floor) before dropping a column. */
    fun gridFit(inner: Float, values: List<String>, maxCols: Int, fontScale: Float): Pair<Int, Float> {
        val opts = listOf(4, 2, 1).filter { it <= maxCols }
        if (values.isEmpty()) return maxCols to 16f
        val floor = TextFit.minSp(fontScale)
        for (cols in opts) {
            val lo = if (cols == 4) maxOf(14f, floor) else floor
            var sp = 16f
            while (sp >= lo) {
                if (rowsFit(values, cols, sp, inner, fontScale)) return cols to sp
                sp -= 1f
            }
        }
        return 1 to floor
    }

    /** One compact row of up to [values].size tiles: the most tiles (in priority order) that fit at the largest size down to the legible floor. */
    fun compactRow(values: List<String>, inner: Float, fontScale: Float): Pair<Int, Float> {
        val floor = TextFit.minSp(fontScale)
        for (k in values.size downTo 1) {
            var sp = 16f
            while (sp >= floor) { if (rowsFit(values.take(k), k, sp, inner, fontScale)) return k to sp; sp -= 1f }
        }
        return 1 to floor
    }

    /** Stage legend lines for [entries] across [inner] dp (dot + text each). */
    fun legendLines(entries: List<String>, inner: Float, fontScale: Float): List<List<String>> =
        TextFit.chunkByWidth(entries, 10f, fontScale, inner, extraPerEntry = 13f)

    private fun rows(prefix: String, n: Int, dp: Float) = (1..n).map { Block("$prefix$it", dp) }

    /** Largest number of rows (up to [maxRows]) for which a graphic of at least [minDp] still fits; returns graphic size and rows. */
    private fun fitStack(avail: Float, rowDp: Float, maxRows: Int, gap: Float, minDp: Float, maxDp: Float, maxByWidth: Float): Pair<Int, Int> {
        for (r in maxRows downTo 0) {
            val g = avail - r * rowDp - (if (r > 0) gap else 0f)
            if (g >= minDp) return minOf(g, maxDp, maxByWidth).toInt() to r
        }
        return minOf(avail, maxDp, maxByWidth).coerceAtLeast(minDp.coerceAtMost(avail)).toInt() to 0
    }

    private fun ringNumeralSp(ringDp: Int, strokeDp: Float, chars: Int): Float {
        val inner = ringDp - 2 * strokeDp - 8
        val byWidth = inner / (0.56f * chars)
        return minOf(ringDp * 0.3f, byWidth).coerceAtLeast(WidgetTokens.MIN_RING_NUMERAL_SP)
    }

    fun ringStroke(ringDp: Int): Float = if (ringDp < 64) 6f else if (ringDp < 90) 8f else 10f

    /** [chars] is the length of the longest text drawn inside a ring or gauge (so it never overflows). */
    fun plan(requested: WidgetStyle, w: Float, h: Float, fontScale: Float, chars: Int = 6, heroText: String = "", values: List<String> = emptyList()): Plan {
        val sc = SizeClass.of(w, h)
        val p = pad(sc, h)
        // A one-row strip cannot hold a hero card: every Last activity style becomes the one-liner.
        val style = if (requested.kind == WidgetKind.LastActivity && h < 90f && requested != WidgetStyle.ActivityGrid) WidgetStyle.ActivityLine else requested
        val avail = availDp(w, h, fontScale)
        val t = ts(fontScale)
        val inner = (w - 2 * p).coerceAtLeast(0f)
        val heroAvail = when (style) {
            WidgetStyle.LoadBars, WidgetStyle.LoadTrend -> if (w >= 200f) (w * 0.34f).coerceIn(84f, 124f) else inner * 0.5f
            WidgetStyle.SleepBar -> if (w >= 200f) inner * 0.6f else inner
            WidgetStyle.SleepTimeline -> if (w >= 200f) inner * 0.6f else inner
            else -> inner
        }
        val hero = fitHero(heroText, heroSp(sc, fontScale), heroAvail, fontScale, avail * 0.45f)
        val big = sc == SizeClass.XLarge
        fun make(side: Boolean, heroDp: Int, heroSp: Float, blocks: Set<String>, used: Float, perRow: Int = 1, availW: Float = heroAvail, tileDp: Float = 0f) =
            Plan(style, sc, p, side, heroDp, heroSp, blocks, perRow, used, avail, availW, tileDp)

        return when (style) {
            WidgetStyle.TodayRing -> {
                val side = w >= 200f
                if (side) {
                    val ring = minOf(avail, inner * 0.42f, 200f).coerceAtLeast(minOf(48f, avail)).toInt()
                    val rows = (avail / (20f * t)).toInt().coerceIn(1, MAX_ROWS)
                    make(true, ring, ringNumeralSp(ring, ringStroke(ring), chars), rows("r", rows, 20f * t).map { it.id }.toSet(), maxOf(ring.toFloat(), rows * 20f * t))
                } else {
                    val (ring, rows) = fitStack(avail, 20f * t, MAX_ROWS, 6f, 64f, 200f, inner)
                    make(false, ring, ringNumeralSp(ring, ringStroke(ring), chars), rows("r", rows, 0f).map { it.id }.toSet(), ring + rows * 20f * t + if (rows > 0) 6f else 0f)
                }
            }
            WidgetStyle.TodayStack -> {
                val bl = listOf(Block("label", 14f * t), Block("bar", 12f)) + rows("r", MAX_ROWS, 20f * t)
                val heroCost = heroLineDp(hero, fontScale)
                val b = budget(avail - heroCost, bl)
                make(false, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }
            WidgetStyle.TodayStripe -> {
                val chipW = 60f
                val per = (inner / chipW).toInt().coerceIn(2, 5)
                val rowDp = stripeRowDp(fontScale)
                val lines = (avail / rowDp).toInt().coerceIn(1, 3)
                val chips = (if (avail < rowDp) 1 else per * lines).coerceAtMost(MAX_ROWS + 1)
                val rowsUsed = ceil(chips / per.toFloat()).toInt()
                make(false, 0, (WidgetTokens.TITLE_SP + 4).coerceAtLeast(16f), (1..chips).map { "c$it" }.toSet(), rowsUsed * rowDp, per)
            }

            WidgetStyle.RecoveryRing -> {
                val side = w >= 200f
                if (side) {
                    val ring = minOf(avail, inner * 0.42f, 200f).coerceAtLeast(minOf(48f, avail)).toInt()
                    val b = budget(avail, listOf(Block("level", 20f * t), Block("ttf", 16f * t)))
                    make(true, ring, ringNumeralSp(ring, ringStroke(ring), 4), b, ring.toFloat())
                } else {
                    val (ring, rows) = fitStack(avail, 18f * t, 2, 4f, 64f, 200f, inner)
                    val ids = listOf("level", "ttf").take(rows).toSet()
                    make(false, ring, ringNumeralSp(ring, ringStroke(ring), 4), ids, ring + rows * 18f * t + if (rows > 0) 4f else 0f)
                }
            }
            WidgetStyle.RecoveryGauge -> {
                var chosen: Plan? = null
                for (r in 2 downTo 0) {
                    val gh = avail - r * 18f * t - (if (r > 0) 4f else 0f)
                    val gw = minOf(inner, (gh - 6f) * 2f, 300f)
                    if (gw >= 96f || r == 0) {
                        val g = gw.coerceAtLeast(80f).coerceAtMost(inner.coerceAtLeast(80f))
                        chosen = make(false, g.toInt(), ringNumeralSp(g.toInt() / 2 + 20, 10f, 4).coerceAtMost(g * 0.26f).coerceAtLeast(WidgetTokens.MIN_RING_NUMERAL_SP),
                            listOf("level", "ttf").take(r).toSet(), g / 2f + 6f + r * 18f * t)
                        break
                    }
                }
                chosen!!
            }
            WidgetStyle.RecoveryPill -> {
                val bl = listOf(Block("pill", 30f), Block("ttf", 18f * t), Block("label", 14f * t))
                val heroCost = heroLineDp(hero, fontScale)
                val b = budget(avail - heroCost, bl)
                make(false, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }

            WidgetStyle.ActivityHero -> {
                val perRow = listOf(3, 2, 1).filter { it <= (if (w >= 220f) 3 else 2) }.firstOrNull { c -> values.all { TextFit.fits(it, 13f, fontScale, inner / c - 4f) } } ?: 1
                val heroCost = heroLineDp(hero, fontScale)
                val bl = listOf(Block("header", 16f * t), Block("s1", 18f * t), Block("s2", 18f * t))
                val b = budget(avail - heroCost, bl)
                make(false, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat(), perRow)
            }
            WidgetStyle.ActivityGrid -> if (h < 90f) {
                // A short strip: one row of compact tiles, as many as fit (in priority order) at the largest legible size.
                val (k0, vsp) = compactRow(values.ifEmpty { listOf("") }, inner, fontScale)
                val k = if (avail < 26f) 1 else k0
                val tileH = avail.coerceIn(26f, 44f)
                make(false, 0, vsp, (1..k).map { "t$it" }.toSet(), tileH, k, availW = tileWidth(inner, k), tileDp = tileH)
            } else {
                val (cols0, vsp) = gridFit(inner, values, if (w >= 250f) 4 else 2, fontScale)
                val minTile = 30f * t + 14f
                val headerDp = 18f * t
                // Tall enough for two tile rows: a 2x2 grid uses the height better than one long row of four.
                val cols = if (cols0 == 4 && avail >= headerDp + 2 * (minTile + 4f)) 2 else cols0
                val maxRows = ceil(4f / cols).toInt()
                val rowsFit = ((avail - headerDp) / (minTile + 4f)).toInt()
                val showHeader = rowsFit >= 1
                val gridRows = (if (showHeader) rowsFit else (avail / (minTile + 4f)).toInt()).coerceIn(1, maxRows)
                val tiles = (if (avail < minTile + 4f) 1 else gridRows * cols).coerceAtMost(4)
                val tileH = ((avail - (if (showHeader) headerDp else 0f)) / gridRows - 4f).coerceIn(minTile, 64f * t)
                val ids = (if (showHeader) listOf("header") else emptyList()) + (1..tiles).map { "t$it" }
                make(false, 0, vsp, ids.toSet(), (if (showHeader) headerDp else 0f) + gridRows * (minTile + 4f), cols, availW = tileWidth(inner, cols), tileDp = tileH)
            }
            WidgetStyle.ActivityLine -> {
                val sp = if (h < 64f) 14f else when (sc) { SizeClass.Small -> 16f; SizeClass.Medium -> 18f; SizeClass.Large -> 22f; SizeClass.XLarge -> 26f }
                val shown = fitHero(heroText, SizeRules.cappedSp(sp, fontScale, HERO_CAP).coerceAtLeast(WidgetTokens.MIN_RING_NUMERAL_SP), inner, fontScale)
                val main = shown * 1.25f * fontScale.coerceIn(1f, HERO_CAP)
                val bl = listOf(Block("title", 16f * t), Block("meta", 18f * t))
                val b = budget(avail - main, bl)
                make(false, 0, shown, b, main + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }

            WidgetStyle.LoadBars, WidgetStyle.LoadTrend -> {
                val side = w >= 200f
                val heroCost = heroLineDp(hero, fontScale)
                if (side) {
                    val b = budget(avail - heroCost - 14f * t, listOf(Block("comment", 18f * t), Block("vo2", 18f * t)))
                    val chart = (avail - 14f).coerceIn(34f, 130f).toInt()
                    make(true, chart, hero, b + "label", maxOf(chart + 14f, heroCost + 14f * t))
                } else {
                    val b = budget(avail - heroCost - 40f, listOf(Block("comment", 18f * t), Block("vo2", 18f * t)))
                    val used = heroCost + b.size * 18f * t
                    val chart = (avail - used - 12f - 12f).coerceIn(28f, 120f).toInt()
                    make(false, chart, hero, b, used + chart + 12f)
                }
            }
            WidgetStyle.LoadDial -> {
                val side = w >= 200f
                if (side) {
                    val dial = minOf(avail, inner * 0.46f, 200f).coerceAtLeast(minOf(64f, avail)).toInt()
                    val b = budget(avail, listOf(Block("comment", 20f * t), Block("vo2", 18f * t)))
                    make(true, dial, ringNumeralSp(dial, 10f, 4), b, dial.toFloat())
                } else {
                    val (dial, rows) = fitStack(avail, 18f * t, 2, 4f, 72f, 200f, inner)
                    make(false, dial, ringNumeralSp(dial, 10f, 4), listOf("comment", "vo2").take(rows).toSet(), dial + rows * 18f * t + if (rows > 0) 4f else 0f)
                }
            }

            WidgetStyle.SleepBar -> {
                val legendN = if (values.isEmpty()) 1 else legendLines(values, inner, fontScale).size
                val side = w >= 200f
                val heroCost = heroLineDp(hero, fontScale)
                val bl = if (side) listOf(Block("score", 0f), Block("window", 0f), Block("bar", 16f), Block("legend", 16f * t * legendN), Block("note", 14f * t))
                else listOf(Block("score", 16f * t), Block("bar", 16f), Block("window", 16f * t), Block("legend", 16f * t * legendN), Block("note", 14f * t))
                val b = budget(avail - heroCost - (if (side) 0f else 14f * t), bl)
                make(side, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }
            WidgetStyle.SleepRing -> {
                val side = w >= 200f
                if (side) {
                    val ring = minOf(avail, inner * 0.42f, 200f).coerceAtLeast(minOf(48f, avail)).toInt()
                    val b = budget(avail, listOf(Block("dur", 28f * t), Block("window", 16f * t), Block("legend", 16f * t)))
                    make(true, ring, ringNumeralSp(ring, ringStroke(ring), 3), b, ring.toFloat())
                } else {
                    val (ring, rows) = fitStack(avail, 20f * t, 2, 4f, 64f, 200f, inner)
                    make(false, ring, ringNumeralSp(ring, ringStroke(ring), 3), listOf("dur", "window").take(rows).toSet(), ring + rows * 20f * t + if (rows > 0) 4f else 0f)
                }
            }
            WidgetStyle.SleepTimeline -> {
                val heroCost = heroLineDp(hero, fontScale)
                val bl = listOf(Block("axis", 16f * t)) + rows("l", 4, 18f * t)
                val b = budget(avail - heroCost, bl)
                make(false, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }

            WidgetStyle.FitnessGrid -> {
                val (cols0, vsp) = gridFit(inner, values, if (w >= 250f) 4 else 2, fontScale)
                val minTile = 30f * t + 14f
                val headDp = 22f * t
                val cols = if (cols0 == 4 && avail >= headDp + 2 * (minTile + 4f)) 2 else cols0
                val thrDp = 20f * t
                val maxRows = ceil(MAX_PRED / cols.toFloat()).toInt()
                val fit = ((avail - headDp) / (minTile + 4f)).toInt()
                val head = fit >= 1
                val gridRows = (if (head) fit else (avail / (minTile + 4f)).toInt()).coerceIn(1, maxRows)
                val n = (if (avail < minTile + 4f) 1 else gridRows * cols).coerceAtMost(MAX_PRED)
                val leftover = avail - (if (head) headDp else 0f) - gridRows * (minTile + 4f)
                // Tall cell: spend the spare height on a threshold pace row first, then on taller tiles, so no band stays empty.
                val thr = head && leftover >= thrDp
                val spare = leftover - (if (thr) thrDp else 0f)
                val tileH = (minTile + (spare / gridRows).coerceIn(0f, 80f))
                val ids = (if (head) listOf("head") else emptyList()) + (1..n).map { "p$it" } + (if (thr) listOf("thr") else emptyList())
                make(false, 0, vsp, ids.toSet(), (if (head) headDp else 0f) + gridRows * (tileH + 4f) + (if (thr) thrDp else 0f), cols, availW = tileWidth(inner, cols), tileDp = tileH)
            }
            WidgetStyle.FitnessVo2 -> {
                val heroCost = heroLineDp(hero, fontScale)
                val bl = listOf(Block("label", 14f * t), Block("thr", 18f * t)) + rows("p", MAX_PRED, 18f * t)
                val b = budget(avail - heroCost, bl)
                make(false, 0, hero, b, heroCost + bl.filter { it.id in b }.sumOf { it.dp.toDouble() }.toFloat())
            }
            WidgetStyle.OverviewGrid -> make(false, 0, 14f, emptySet(), 0f)
            WidgetStyle.FitnessLadder -> {
                val rowDp = 24f * t
                val n = (avail / rowDp).toInt().coerceIn(1, MAX_PRED + 1)
                make(false, 0, 18f, (1..n).map { "r$it" }.toSet(), n * rowDp)
            }
        }
    }

    /** Height of one line of icon-over-number chips. */
    fun stripeRowDp(fontScale: Float): Float = 22f + 16f * ts(fontScale)

    /** How much a plan shows: used to tell whether a layout dropped content that the real cell had room for. */
    fun contentScore(p: Plan): Int = p.blocks.size * 4 + (if (p.side) 2 else 0) + p.perRow

    const val MAX_ROWS = 4
    const val MAX_PRED = 4

    /** Race distance in metres from a label such as "5K", "10K", "Half marathon", "Marathon"; null when unknown. */
    fun raceMeters(label: String): Double? {
        val l = label.trim().lowercase().replace(" ", "")
        return when {
            l == "5k" -> 5000.0
            l == "10k" -> 10000.0
            l == "hm" || l.startsWith("half") || l == "21k" || l == "21.1k" -> 21097.5
            l == "m" || l.startsWith("marathon") || l == "42k" || l == "42.2k" || l == "full" -> 42195.0
            else -> null
        }
    }

    /** "H:MM:SS" / "MM:SS" race time to seconds. */
    fun parseRaceTime(t: String): Int? {
        val parts = t.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) { 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> null }
    }

    /** Average pace for a prediction row, in the user's unit; null when the distance is unknown. */
    fun predictionPace(label: String, time: String, unit: app.cairn.core.model.DistanceUnit): String? {
        val m = raceMeters(label) ?: return null
        val s = parseRaceTime(time) ?: return null
        return Fmt.pace((s / (m / 1000.0)).toInt(), unit)
    }
}
