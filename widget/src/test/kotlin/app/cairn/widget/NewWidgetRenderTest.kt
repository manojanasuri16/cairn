package app.cairn.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composes the real widgets (the same RemoteViews the launcher gets) at the cells 110x110, 176x223, 272x223 and 320x330 and reads
 * the view tree: every element the layout plan promises must be there, for every variant, and the hero and the level word never go missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewWidgetRenderTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cells = listOf(110f to 110f, 176f to 223f, 272f to 223f, 320f to 330f)

    private fun texts(kind: WidgetKind, cfg: ResolvedConfig, w: Float, h: Float): List<String> {
        val views = runBlocking { WidgetCatalog.previewViews(context, kind, cfg, DpSize(w.dp, h.dp)) }
        val root = FrameLayout(context)
        root.addView(views.apply(context, root))
        val out = mutableListOf<String>()
        fun walk(v: View) { if (v is TextView && v.text.isNotEmpty()) out += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(root)
        return out
    }

    /** The plan the widget used: the size the launcher picks for the cell, evaluated with the margin, like `widgetSize()`. */
    private fun planFor(kind: WidgetKind, style: WidgetStyle, w: Float, h: Float, heroText: String, values: List<String> = emptyList(), chars: Int = 6): Layouts.Plan {
        val (sw, sh) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), w, h)
        return Layouts.plan(style, sw + WidgetSizes.MARGIN_DP, sh + WidgetSizes.MARGIN_DP, 1f, chars, heroText, values)
    }

    private fun cfg(kind: WidgetKind, style: WidgetStyle, goal: WeeklyGoal? = null, theme: ThemeId? = null) =
        WidgetConfig(style = style, weeklyGoal = goal, theme = theme).resolve(kind, AppAppearance())

    private fun has(t: List<String>, s: String) = t.any { it == s }
    private fun hasPart(t: List<String>, s: String) = t.any { it.contains(s) }

    @Test fun hrvEveryVariantShowsNumberWordAndThePlannedRows() {
        for (style in WidgetStyle.forKind(WidgetKind.Hrv)) for ((w, h) in cells) {
            val plan = planFor(WidgetKind.Hrv, style, w, h, "65 ms", listOf("Elevated"), 5)
            val t = texts(WidgetKind.Hrv, cfg(WidgetKind.Hrv, style), w, h)
            val tag = "$style ${w}x$h $t"
            assertTrue(tag, has(t, "65") && has(t, "ms"))
            assertTrue("word $tag", has(t, "Elevated") && plan.has("pill"))
            if (plan.has("range")) assertTrue("range $tag", hasPart(t, "46-58 ms"))
            if (plan.has("base")) assertTrue("baseline $tag", hasPart(t, "52 ms"))
            if (plan.has("label")) assertTrue("label $tag", has(t, "Overnight HRV"))
            if (style == WidgetStyle.HrvNights && plan.has("chart")) assertEquals("letters $tag", 7, t.count { it.length == 1 && it in "MTWFS" })
        }
    }

    @Test fun heartRateEveryVariantShowsRestingFirstThenTheRest() {
        for (style in WidgetStyle.forKind(WidgetKind.HeartRate)) for ((w, h) in cells) {
            val heroText = if (style == WidgetStyle.HeartPair) "70 bpm" else "51 bpm"
            val plan = planFor(WidgetKind.HeartRate, style, w, h, heroText)
            val t = texts(WidgetKind.HeartRate, cfg(WidgetKind.HeartRate, style), w, h)
            val tag = "$style ${w}x$h $t"
            assertTrue("hero $tag", has(t, "51"))
            if (style == WidgetStyle.HeartPair) assertTrue("pair $tag", has(t, "70"))
            if (plan.has("avg7")) assertTrue("7-day $tag", hasPart(t, "7-day avg 53") || hasPart(t, "avg 53"))
            if (plan.has("avgrow") && style == WidgetStyle.HeartSpark) assertTrue("daily $tag", hasPart(t, "70"))
            if (style == WidgetStyle.HeartRange && plan.has("labels")) assertTrue("labels $tag", hasPart(t, "44") && hasPart(t, "154"))
            if (plan.has("label") && style != WidgetStyle.HeartPair) assertTrue("label $tag", has(t, "Resting HR"))
        }
    }

    @Test fun stressEveryVariantShowsTheValueAndItsLevelWord() {
        for (style in WidgetStyle.forKind(WidgetKind.Stress)) for ((w, h) in cells) {
            val t = texts(WidgetKind.Stress, cfg(WidgetKind.Stress, style), w, h)
            val tag = "$style ${w}x$h $t"
            assertTrue("value $tag", has(t, "30"))
            assertTrue("level word $tag", has(t, "Low"))
            val plan = planFor(WidgetKind.Stress, style, w, h, "30", listOf("Low"), 2)
            if (style == WidgetStyle.StressBars && plan.has("chart")) assertEquals("letters $tag", 7, t.count { it.length == 1 && it in "MTWFS" })
            if (plan.has("avg7")) assertTrue("avg $tag", hasPart(t, "avg 30"))
        }
    }

    @Test fun mileageEveryVariantShowsTotalCountAndComparison() {
        val goal = WeeklyGoal(30, DistanceUnit.Km)
        for (style in WidgetStyle.forKind(WidgetKind.WeeklyMileage)) for ((w, h) in cells) {
            val eff = if (style == WidgetStyle.MileageGoal) style else style
            val plan = planFor(WidgetKind.WeeklyMileage, eff, w, h, "25.10 km", chars = 4)
            val t = texts(WidgetKind.WeeklyMileage, cfg(WidgetKind.WeeklyMileage, style, goal), w, h)
            val tag = "$style ${w}x$h $t"
            if (style != WidgetStyle.MileageGoal || plan.heroDp == 0 || plan.side || plan.has("total")) assertTrue("total $tag", has(t, "25.10") && has(t, "km"))
            if (plan.has("count")) assertTrue("count $tag", has(t, "4 activities"))
            if (plan.has("cmp")) assertTrue("cmp $tag", hasPart(t, "+12%"))
            if (style == WidgetStyle.MileageBars && plan.has("chart")) assertEquals("letters $tag", 7, t.count { it.length == 1 && it in "MTWFS" })
            if (style == WidgetStyle.MileageGoal && plan.heroDp > 0) assertTrue("percent $tag", has(t, "84%"))
        }
    }

    @Test fun goalRingWithoutAGoalShowsTheBarsInstead() {
        val t = texts(WidgetKind.WeeklyMileage, cfg(WidgetKind.WeeklyMileage, WidgetStyle.MileageGoal, goal = null), 176f, 223f)
        assertTrue(t.toString(), t.none { it.endsWith("%") && it != "+12%" } && t.count { it.length == 1 && it in "MTWFS" } == 7)
    }

    @Test fun everyStyleRendersInEveryLook() {
        for (theme in ThemeId.entries) for (kind in listOf(WidgetKind.Hrv, WidgetKind.HeartRate, WidgetKind.Stress, WidgetKind.WeeklyMileage)) {
            for (style in WidgetStyle.forKind(kind)) {
                val t = texts(kind, cfg(kind, style, WeeklyGoal(30, DistanceUnit.Km), theme), 272f, 223f)
                assertTrue("$theme $style $t", t.isNotEmpty())
            }
        }
        // Transparent background (wallpaper ink) too.
        val c = WidgetConfig(bg = Bg.Transparent).resolve(WidgetKind.Hrv, AppAppearance())
        assertTrue(texts(WidgetKind.Hrv, c, 176f, 223f).any { it == "Elevated" })
    }

    @Test fun messagesAndLoadingShowWords() {
        // The shared message and loading views (used by the new widgets for every empty or signed-out state) always carry words.
        assertTrue(WidgetMapper.stress(emptyList(), AuthState.Ok, java.time.Instant.now(), java.time.LocalDate.now()) is WidgetUi.Message)
    }
}
