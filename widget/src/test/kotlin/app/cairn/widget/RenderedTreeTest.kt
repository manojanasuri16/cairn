package app.cairn.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the real RemoteViews (the same translation the launcher gets) and reads the view tree, to catch tiles Glance drops on the way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenderedTreeTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun texts(kind: WidgetKind, cfg: ResolvedConfig, size: DpSize): List<String> {
        val views = runBlocking { WidgetCatalog.previewViews(context, kind, cfg, size) }
        val root = FrameLayout(context)
        root.addView(views.apply(context, root))
        val out = mutableListOf<String>()
        fun walk(v: View) { if (v is TextView && v.text.isNotEmpty()) out += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(root)
        return out
    }

    @Test fun overviewRendersFourTilesInOrder() {
        val cfg = WidgetConfig().resolve(WidgetKind.Overview, AppAppearance())
        val t = texts(WidgetKind.Overview, cfg, DpSize(176.dp, 222.dp))
        val labels = t.filter { it in OverviewId.entries.map { e -> e.label } }
        assertEquals(t.toString(), listOf("Steps", "Sleep", "Recovery", "Resting HR"), labels)
    }

    private val cells = listOf(110f to 110f, 176f to 222f, 164f to 206f, 272f to 223f, 320f to 330f)
    private fun sel(kind: WidgetKind, w: Float, h: Float) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), w, h).let { it.first + WidgetSizes.MARGIN_DP to it.second + WidgetSizes.MARGIN_DP }
    private fun count(t: List<String>, values: List<String>) = t.count { it in values }

    @Test fun overviewRendersTheFirstNInOrderAtLauncherReportedSizes() {
        val order = listOf(OverviewId.Steps, OverviewId.Sleep, OverviewId.Recovery, OverviewId.RestingHr, OverviewId.Calories, OverviewId.LoadRatio)
        for (n in 1..6) for ((w, h) in cells) {
            val cfg = WidgetConfig(metrics = order.take(n)).resolve(WidgetKind.Overview, AppAppearance())
            val shown = OverviewLayout.planForCell(n, w, h, 1f, WidgetSizes.byKind.getValue(WidgetKind.Overview)).shown
            val labels = texts(WidgetKind.Overview, cfg, DpSize(w.dp, h.dp)).filter { it in OverviewId.entries.map { e -> e.label } }
            assertEquals("$n stats at ${w}x$h", order.take(n).take(shown).map { it.label }, labels)
        }
        // A cell a little under 170 dp wide (a launcher reports the widget view, not the cell) still holds the default four as a 2x2.
        assertEquals(4, OverviewLayout.planForCell(4, 164f, 206f, 1f, WidgetSizes.byKind.getValue(WidgetKind.Overview)).shown)
        assertEquals(4, OverviewLayout.planForCell(4, 150f, 200f, 1f, WidgetSizes.byKind.getValue(WidgetKind.Overview)).shown)
    }

    @Test fun todayRowsAreAllPresentThatTheRuleShows() {
        val values = listOf("7h 41m", "52 bpm", "82%", "1,820")
        for (style in WidgetStyle.forKind(WidgetKind.Today).filter { it != WidgetStyle.TodayStripe }) for ((w, h) in cells) {
            val (sw, sh) = sel(WidgetKind.Today, w, h)
            val plan = Layouts.plan(style, sw, sh, 1f, chars = 5, heroText = "8,432")
            val t = texts(WidgetKind.Today, WidgetConfig(style = style).resolve(WidgetKind.Today, AppAppearance()), DpSize(w.dp, h.dp))
            assertEquals("$style ${w}x$h", plan.count("r").coerceAtMost(values.size), count(t, values))
        }
    }

    @Test fun statGridAndRaceGridShowEveryTileTheRuleShows() {
        val act = listOf("10.20 km", "52 min", "5'07\" /km", "152 bpm")
        val times = listOf("21:40", "45:10", "1:39:30", "3:30:12")
        for ((w, h) in cells + listOf(272f to 75f)) {
            val (aw, ah) = sel(WidgetKind.LastActivity, w.coerceAtLeast(250f), h)
            val ap = Layouts.plan(WidgetStyle.ActivityGrid, aw, ah, 1f, heroText = "", values = act)
            val at = texts(WidgetKind.LastActivity, WidgetConfig(style = WidgetStyle.ActivityGrid).resolve(WidgetKind.LastActivity, AppAppearance()), DpSize(w.coerceAtLeast(250f).dp, h.dp))
            assertEquals("stat grid ${w}x$h", ap.count("t"), count(at, act))
            val (fw, fh) = sel(WidgetKind.Fitness, w.coerceAtLeast(250f), h.coerceAtLeast(110f))
            val fp = Layouts.plan(WidgetStyle.FitnessGrid, fw, fh, 1f, heroText = "52", values = times)
            val ft = texts(WidgetKind.Fitness, WidgetConfig(style = WidgetStyle.FitnessGrid).resolve(WidgetKind.Fitness, AppAppearance()), DpSize(w.coerceAtLeast(250f).dp, h.coerceAtLeast(110f).dp))
            assertEquals("race grid ${w}x$h", fp.count("p"), count(ft, times))
        }
    }
}
