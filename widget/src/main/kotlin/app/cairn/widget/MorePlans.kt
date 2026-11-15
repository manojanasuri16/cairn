package app.cairn.widget

import app.cairn.core.designsystem.SizeClass

/**
 * Layout rules of the HRV, Heart rate, Stress and Weekly mileage widgets. Same contract as [Layouts.plan]: the hero is always
 * kept, secondary blocks are added in priority order only while they fit, and a chart, ring or gauge takes whatever height is
 * left (within its limits), so no band of the card stays empty. In cells at least 200 dp wide the hero and its pill sit on the
 * left and the chart on the right (`side`).
 *
 * Block ids: pill, chart, range, base, label, avg7, avgrow, labels, count, cmp, goal, level, total, bar.
 * [Layouts.Plan.heroDp] is the chart height (bar, line, bars), the ring size or the gauge width; 0 means the text-only fallback.
 */
internal object MorePlans {
    val kinds = setOf(WidgetKind.Hrv, WidgetKind.HeartRate, WidgetKind.Stress, WidgetKind.WeeklyMileage)

    /** Height of a pill of one or two lines, with the gap above it. */
    fun pillDp(text: String, availW: Float, fs: Float): Float {
        val one = text.isNotEmpty() && TextFit.widthDp(text, 12f, fs) * 1.08f + 24f <= availW
        return 14f + 15f * Layouts.ts(fs) * (if (one) 1 else 2)
    }

    /** Width of the hero column when the chart sits beside it. */
    fun leftWidth(w: Float, pillText: String, fs: Float): Float {
        val base = (w * 0.34f).coerceIn(84f, 124f)
        val pill = if (pillText.isEmpty()) 0f else minOf(TextFit.widthDp(pillText, 12f, fs) * 1.08f + 24f, w * 0.45f)
        return maxOf(base, pill)
    }

    fun plan(style: WidgetStyle, w: Float, h: Float, fs: Float, chars: Int, heroText: String, values: List<String>): Layouts.Plan {
        val sc = SizeClass.of(w, h)
        val pad = Layouts.pad(sc, h)
        val t = Layouts.ts(fs)
        val avail = Layouts.availDp(w, h, fs)
        val inner = (w - 2 * pad).coerceAtLeast(0f)
        val side = w >= 200f
        val pillText = values.firstOrNull().orEmpty()
        // The mileage subtitle ("+14% vs last week") must fit beside the chart, so it sizes the left column like a pill does.
        val leftText = if (style.kind == WidgetKind.WeeklyMileage) "+99% vs last week" else pillText

        fun hero(width: Float) = Layouts.fitHero(heroText, Layouts.heroSp(sc, fs), width, fs, avail * 0.45f)
        fun make(side: Boolean, heroDp: Int, heroSp: Float, blocks: Set<String>, used: Float, perRow: Int = 1, heroAvail: Float = inner) =
            Layouts.Plan(style, sc, pad, side, heroDp, heroSp, blocks, perRow, used, avail, heroAvail)
        fun cost(rows: List<Layouts.Block>, ids: Set<String>) = rows.filter { it.id in ids && it.id != "chart" }.sumOf { it.dp.toDouble() }.toFloat()

        /** Hero, then [rows] in priority order; the "chart" row (cost chartMin + extra + gap) grows into the spare height up to [chartMax]. */
        fun stacked(rows: List<Layouts.Block>, chartMin: Float = 0f, chartMax: Float = 0f, extra: Float = 0f): Layouts.Plan {
            val hs = hero(inner)
            val heroCost = Layouts.heroLineDp(hs, fs)
            val b = Layouts.budget(avail - heroCost, rows)
            val fixed = cost(rows, b)
            val chart = if ("chart" in b) maxOf(chartMin, minOf(chartMax, inner, chartMin + (avail - heroCost - fixed - (chartMin + extra + 6f)).coerceAtLeast(0f))) else 0f
            val used = heroCost + fixed + if ("chart" in b) chart + extra + 6f else 0f
            return make(false, chart.toInt(), hs, b, used)
        }

        /** Hero and [left] rows in a column on the left, the chart and [right] rows on the right. */
        fun beside(left: List<Layouts.Block>, right: List<Layouts.Block>, chartMin: Float, chartMax: Float, extra: Float): Layouts.Plan {
            val lw = leftWidth(w, leftText, fs)
            val hs = hero(lw)
            val heroCost = Layouts.heroLineDp(hs, fs)
            val lb = Layouts.budget(avail - heroCost, left)
            val leftUsed = heroCost + cost(left, lb)
            val rb = Layouts.budget(avail, right)
            val fixed = cost(right, rb)
            val chart = if ("chart" in rb) maxOf(chartMin, minOf(chartMax, chartMin + (avail - fixed - (chartMin + extra + 6f)).coerceAtLeast(0f))) else 0f
            val rightUsed = fixed + if ("chart" in rb) chart + extra + 6f else 0f
            return make(true, chart.toInt(), hs, lb + rb, maxOf(leftUsed, rightUsed), heroAvail = lw)
        }

        fun blk(id: String, dp: Float) = Layouts.Block(id, dp)
        val pillStack = pillDp(pillText, inner, fs)
        val pillLeft = pillDp(pillText, leftWidth(w, leftText, fs), fs)
        val row = 18f * t
        val lab = 14f * t
        val letters = 12f * t

        return when (style) {
            WidgetStyle.HrvRange ->
                if (side) beside(listOf(blk("pill", pillLeft), blk("label", lab)), listOf(blk("chart", 22f + 6f), blk("range", row), blk("base", 16f * t)), 22f, 44f, 0f)
                else stacked(listOf(blk("pill", pillStack), blk("chart", 22f + 6f), blk("range", row), blk("label", lab), blk("base", 16f * t)), 22f, 44f)
            WidgetStyle.HrvNights ->
                if (side) beside(listOf(blk("pill", pillLeft), blk("label", lab)), listOf(blk("chart", 36f + letters + 6f), blk("range", row)), 36f, 170f, letters)
                else stacked(listOf(blk("pill", pillStack), blk("chart", 36f + letters + 6f), blk("range", row), blk("label", lab)), 36f, 170f, letters)
            WidgetStyle.HrvPill -> stacked(listOf(blk("pill", pillStack), blk("range", row), blk("base", 16f * t), blk("label", lab)))

            WidgetStyle.HeartSpark ->
                if (side) beside(listOf(blk("avg7", row), blk("label", lab)), listOf(blk("chart", 28f + 6f), blk("avgrow", row)), 28f, 170f, 0f)
                else stacked(listOf(blk("avg7", row), blk("chart", 28f + 6f), blk("avgrow", row), blk("label", lab)), 28f, 130f)
            WidgetStyle.HeartRange ->
                if (side) beside(listOf(blk("avg7", row), blk("label", lab)), listOf(blk("chart", 22f + 6f), blk("labels", 16f * t)), 22f, 40f, 0f)
                else stacked(listOf(blk("chart", 22f + 6f), blk("labels", 16f * t), blk("avg7", row), blk("label", lab)), 22f, 40f)
            WidgetStyle.HeartPair -> {
                val half = ((inner - 8f) / 2f).coerceAtLeast(40f)
                val unitless = heroText.isNotEmpty() && !TextFit.fits(heroText, hero(half), fs, half, Layouts.HERO_CAP)
                // Too narrow for "70 bpm" in two columns: the numbers alone get the room and "bpm" moves to its own small line.
                val hs = if (unitless) Layouts.fitHero(heroText.substringBefore(' '), Layouts.heroSp(sc, fs), half, fs, avail * 0.45f) else hero(half)
                val heroCost = Layouts.heroLineDp(hs, fs) + lab + if (unitless) 14f * t else 0f
                val rows = listOf(blk("avg7", row), blk("avgrow", row))
                val b = Layouts.budget(avail - heroCost, rows)
                make(false, 0, hs, b, heroCost + cost(rows, b), perRow = 2, heroAvail = half)
            }

            WidgetStyle.StressPill -> stacked(listOf(blk("pill", pillStack), blk("avg7", row), blk("label", lab)))
            WidgetStyle.StressBars ->
                if (side) beside(listOf(blk("pill", pillLeft), blk("label", lab)), listOf(blk("chart", 36f + letters + 6f), blk("avg7", row)), 36f, 170f, letters)
                else stacked(listOf(blk("pill", pillStack), blk("chart", 36f + letters + 6f), blk("avg7", row), blk("label", lab)), 36f, 170f, letters)
            WidgetStyle.StressGauge -> {
                // The level word must stay visible: below a gauge of 72 dp plus its word, fall back to number and pill.
                var chosen: Layouts.Plan? = null
                for (r in listOf(2, 1)) {
                    val gh = avail - r * row - 4f
                    val gw = minOf(inner, (gh - 6f) * 2f, 300f)
                    if (gw >= 72f) {
                        val g = gw.toInt()
                        chosen = make(false, g, Layouts.ringNumeralSp(g / 2 + 20, 10f, chars.coerceAtLeast(2)).coerceAtMost(g * 0.26f).coerceAtLeast(14f),
                            listOf("level", "avg7").take(r).toSet(), g / 2f + 6f + r * row + 4f)
                        break
                    }
                }
                chosen ?: stacked(listOf(blk("pill", pillStack), blk("avg7", row), blk("label", lab)))
            }

            WidgetStyle.MileageBars ->
                if (side) beside(listOf(blk("count", row), blk("cmp", row), blk("label", lab)), listOf(blk("chart", 36f + letters + 6f)), 36f, 190f, letters)
                else stacked(listOf(blk("count", row), blk("chart", 36f + letters + 6f), blk("cmp", row), blk("label", lab)), 36f, 170f, letters)
            WidgetStyle.MileageTrend ->
                if (side) beside(listOf(blk("cmp", row), blk("count", row), blk("label", lab)), listOf(blk("chart", 30f + 6f)), 30f, 190f, 0f)
                else stacked(listOf(blk("cmp", row), blk("chart", 30f + 6f), blk("count", row), blk("label", lab)), 30f, 130f)
            WidgetStyle.MileageGoal -> {
                if (side) {
                    val ring = minOf(avail, inner * 0.42f, 200f).coerceAtLeast(minOf(48f, avail)).toInt()
                    val rw = (inner - ring - 16f).coerceAtLeast(60f)
                    val hs = hero(rw)
                    val heroCost = Layouts.heroLineDp(hs, fs)
                    val rows = listOf(blk("goal", 16f * t), blk("count", row), blk("cmp", row))
                    val b = Layouts.budget(avail - heroCost, rows)
                    make(true, ring, hs, b, maxOf(ring.toFloat(), heroCost + cost(rows, b)), heroAvail = rw)
                } else {
                    val lineSp = minOf(22f, Layouts.fitHero(heroText, 18f, inner, fs))
                    val totalDp = Layouts.heroLineDp(lineSp, fs)
                    var chosen: Layouts.Plan? = null
                    for ((ids, c) in listOf(setOf("total", "goal") to totalDp + 16f * t + 8f, setOf("total") to totalDp + 4f)) {
                        val ring = minOf(avail - c, 200f, inner)
                        if (ring >= 64f) { chosen = make(false, ring.toInt(), lineSp, ids, ring + c); break }
                    }
                    chosen ?: run {
                        // Too short for a ring and its total: the number with a thin progress bar and the goal line.
                        val hs = hero(inner)
                        val heroCost = Layouts.heroLineDp(hs, fs)
                        val rows = listOf(blk("bar", 18f), blk("goal", 16f * t))
                        val b = Layouts.budget(avail - heroCost, rows)
                        make(false, 0, hs, b, heroCost + cost(rows, b))
                    }
                }
            }
            else -> error("not a MorePlans style: $style")
        }
    }
}
