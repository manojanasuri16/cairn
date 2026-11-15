package app.cairn.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal object SamplePlans {
    private val activityValues = listOf("12.3 km", "1h 05m", "5:30 /mi", "151 bpm")
    private val raceTimes = listOf("25:06", "52:33", "1:58:07", "4:09:57")
    private val legend = listOf("1h 39m", "3h 44m", "1h 40m", "21m")

    fun plan(style: WidgetStyle, w: Float, h: Float, scale: Float): Layouts.Plan = when (style.kind) {
        WidgetKind.Today -> Layouts.plan(style, w, h, scale, chars = 6, heroText = "12,345")
        WidgetKind.Recovery -> Layouts.plan(style, w, h, scale, chars = 4, heroText = "82%")
        WidgetKind.LastActivity -> Layouts.plan(style, w, h, scale, heroText = if (style == WidgetStyle.ActivityLine || h < 90f) "12.3 km · 1h 05m" else "12.3 km", values = activityValues)
        WidgetKind.TrainingLoad -> Layouts.plan(style, w, h, scale, chars = 4, heroText = "1.12")
        WidgetKind.Sleep -> Layouts.plan(style, w, h, scale, chars = 6, heroText = "7h 41m", values = legend)
        WidgetKind.Fitness -> Layouts.plan(style, w, h, scale, heroText = "52", values = raceTimes)
        WidgetKind.Overview -> Layouts.plan(style, w, h, scale)
    }

    /** Smallest cell Android lets the user place, from each provider's widget_info. */
    fun minimum(kind: WidgetKind): Pair<Int, Int> = when (kind) {
        WidgetKind.Today, WidgetKind.Recovery, WidgetKind.Sleep -> 110 to 110
        WidgetKind.LastActivity -> 250 to 40
        WidgetKind.TrainingLoad, WidgetKind.Fitness -> 250 to 110
        WidgetKind.Overview -> 110 to 110
    }

    /** Typical launcher cells: 2x2 (110-180 x 110-230), 3x2, 4x2 (250-330 x 110-230), 4x3 (250-330 x 230-330), 4x1 (250-330 x 55-90). */
    fun typical(kind: WidgetKind): List<Pair<Int, Int>> {
        val (mw, mh) = minimum(kind)
        val ws = listOf(110, 130, 150, 175, 180, 200, 250, 272, 300, 330).filter { it >= mw }
        val hs = listOf(55, 75, 90, 110, 130, 150, 180, 200, 222, 230, 242, 280, 330).filter { it >= mh }
        return ws.flatMap { w -> hs.map { h -> w to h } }
    }

    /** The default style counts sixteen times: it is what most people keep. */
    fun score(kind: WidgetKind, w: Float, h: Float, scale: Float = 1f): Int = WidgetStyle.forKind(kind).sumOf { Layouts.contentScore(plan(it, w, h, scale)) * (if (it == WidgetStyle.default(kind)) 16 else 1) }
}

class WidgetSizesTest {
    @Test fun generate() {
        if (System.getenv("GEN_SIZES") == null) return
        for (kind in WidgetKind.entries) {
            val (mw, mh) = SamplePlans.minimum(kind)
            val ws = (mw..340 step 5).toList(); val hs = (mh..340 step 5).toList()
            val actual = ws.flatMap { w -> hs.map { h -> w to h } }
            val typical = SamplePlans.typical(kind).toSet()
            val weight = actual.map { if (it in typical) 40 else 1 }
            val sc = actual.map { SamplePlans.score(kind, it.first.toFloat(), it.second.toFloat()) }
            val chosen = mutableListOf(actual.indexOf(mw to mh))
            val sel = IntArray(actual.size) { chosen[0] }
            fun better(a: Int, cur: Int, cand: Int): Boolean {
                val (aw, ah) = actual[a]; val c = actual[cur]; val n = actual[cand]
                val cf = c.first <= aw && c.second <= ah; val nf = n.first <= aw && n.second <= ah
                return when { nf && !cf -> true; !nf -> false; else -> n.first * n.second > c.first * c.second }
            }
            fun loss(): Long = actual.indices.sumOf { (weight[it] * maxOf(0, sc[it] - sc[sel[it]])).toLong() }
            var cur = loss()
            while (chosen.size < 16 && cur > 0) {
                var bestC = -1; var bestLoss = cur
                for (c in actual.indices) {
                    if (c in chosen) continue
                    var l = 0L
                    for (a in actual.indices) { val s = if (better(a, sel[a], c)) c else sel[a]; l += weight[a] * maxOf(0, sc[a] - sc[s]) }
                    if (l < bestLoss) { bestLoss = l; bestC = c }
                }
                if (bestC < 0) break
                chosen += bestC
                for (a in actual.indices) if (better(a, sel[a], bestC)) sel[a] = bestC
                cur = bestLoss
            }
            println("SIZES ${kind.name} loss=$cur " + chosen.map { actual[it] }.sortedWith(compareBy({ it.first }, { it.second })).joinToString(", ") { "${it.first} to ${it.second}" })
        }
    }

    @Test fun everyKindHasAtMostSixteenSizesIncludingItsMinimum() {
        for (kind in WidgetKind.entries) {
            val s = WidgetSizes.byKind.getValue(kind)
            assertTrue("$kind ${s.size}", s.size in 1..16)
            assertTrue("$kind lacks its minimum", SamplePlans.minimum(kind) in s)
        }
    }

    @Test fun theSelectedSizeShowsEverythingTheRealCellHasRoomFor() {
        val misses = mutableListOf<String>()
        for (kind in WidgetKind.entries) for ((w, h) in SamplePlans.typical(kind)) {
            val (sw, sh) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), w.toFloat(), h.toFloat())
            for (style in WidgetStyle.forKind(kind)) {
                val real = SamplePlans.plan(style, w.toFloat(), h.toFloat(), 1f)
                val shown = SamplePlans.plan(style, sw.toFloat(), sh.toFloat(), 1f)
                // The default style may lose at most one block and the other styles two (16 sizes cannot hold every threshold of three styles); the measured emulator cells below are exact (16 sizes cannot hold every style's thresholds).
                val slack = if (style == WidgetStyle.default(kind)) 4 else 9
                if (Layouts.contentScore(shown) + slack < Layouts.contentScore(real)) misses += "$style ${w}x$h picked ${sw}x$sh: shows ${shown.blocks.size} blocks/side=${shown.side}/cols=${shown.perRow}, ${real.blocks.size}/${real.side}/${real.perRow} fit"
            }
        }
        assertTrue("${misses.size} cells drop content that fits:\n" + misses.take(25).joinToString("\n"), misses.isEmpty())
    }

    @Test fun measuredEmulatorCells() {
        fun shown(kind: WidgetKind, style: WidgetStyle, w: Float, h: Float): Layouts.Plan {
            val (sw, sh) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), w, h)
            return SamplePlans.plan(style, sw.toFloat(), sh.toFloat(), 1f)
        }
        assertEquals(4, shown(WidgetKind.Today, WidgetStyle.TodayRing, 175f, 222f).count("r"))
        assertTrue(shown(WidgetKind.LastActivity, WidgetStyle.ActivityHero, 272f, 75f).style == WidgetStyle.ActivityLine)
        assertEquals(4, shown(WidgetKind.Fitness, WidgetStyle.FitnessGrid, 272f, 223f).count("p"))
        val sleep = shown(WidgetKind.Sleep, WidgetStyle.SleepBar, 175f, 222f)
        assertTrue(sleep.has("score") && sleep.has("window") && sleep.has("legend"))
    }

    @Test fun noRowIsDroppedWhileHeightRemains() {
        // At the size the rules were given, if one more row of the style's priority list would fit in what is left, it must be there.
        for (kind in WidgetKind.entries) for ((w, h) in SamplePlans.typical(kind)) for (s in listOf(1f, 1.3f, 2f)) for (style in WidgetStyle.forKind(kind)) {
            val p = SamplePlans.plan(style, w.toFloat(), h.toFloat(), s)
            val spare = p.availDp - p.usedDp
            val rowCost = 16f * Layouts.ts(s)
            if (style == WidgetStyle.TodayStack || style == WidgetStyle.FitnessVo2) {
                val max = if (style == WidgetStyle.TodayStack) Layouts.MAX_ROWS else Layouts.MAX_PRED
                val prefix = if (style == WidgetStyle.TodayStack) "r" else "p"
                if (p.count(prefix) < max && p.has("label")) assertTrue("$style ${w}x$h x$s left ${spare}dp but dropped a row", spare < rowCost + 6f || spare < 18f * Layouts.ts(s))
            }
        }
    }
}
