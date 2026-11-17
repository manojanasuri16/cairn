package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Duration.Companion.minutes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composes the real Glance content and inspects the emitted tree, so a tile that is never composed fails here (the layout maths
 * tests cannot see that). Each case uses the size the launcher would select for the cell (the largest listed size that fits).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompositionTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val allLabels = OverviewId.entries.map { it.label }
    private val chosenOrder = listOf(OverviewId.Steps, OverviewId.Sleep, OverviewId.Recovery, OverviewId.RestingHr, OverviewId.Calories, OverviewId.LoadRatio)

    private fun selected(kind: WidgetKind, w: Float, h: Float): DpSize {
        val (sw, sh) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), w, h)
        return DpSize(sw.dp, sh.dp)
    }

    private fun overview(count: Int, cellW: Float, cellH: Float) = runGlanceAppWidgetUnitTest(timeout = 2.minutes) {
        setContext(context)
        val size = selected(WidgetKind.Overview, cellW, cellH)
        setAppWidgetSize(size)
        val chosen = chosenOrder.take(count)
        val expected = OverviewLayout.firstN(chosen, OverviewLayout.planForCell(count, cellW, cellH, 1f, WidgetSizes.byKind.getValue(WidgetKind.Overview), chosen.map { id -> Samples.overview().body.items.firstOrNull { it.id == id } ?: OverviewItem(id, "\u2013") }).shown)
        val cfg = WidgetConfig(metrics = chosen).resolve(WidgetKind.Overview, AppAppearance())
        provideComposable { OverviewWidget().RenderSample(cfg) }
        for (id in OverviewId.entries) {
            val node = onAllNodes(hasText(id.label) or (if (Layouts.shortLabel(id.label).length > 2 && Layouts.shortLabel(id.label) != id.label) hasText(Layouts.shortLabel(id.label)) else hasText(id.label)))
            if (id in expected) node.assertCountEquals(1) else node.assertCountEquals(0)
        }
        check(expected.isNotEmpty())
    }

    @Test fun defaultFourStatsAtATwoCellSizeAreAllComposed() = runGlanceAppWidgetUnitTest(timeout = 2.minutes) {
        setContext(context)
        setAppWidgetSize(selected(WidgetKind.Overview, 176f, 222f))
        val cfg = WidgetConfig().resolve(WidgetKind.Overview, AppAppearance())
        provideComposable { OverviewWidget().RenderSample(cfg) }
        for (label in listOf("Steps", "Sleep", "Recovery", "Resting HR")) onAllNodes(hasText(label) or hasText(Layouts.shortLabel(label))).assertCountEquals(1)
    }

    @Test fun overviewComposesTheFirstNStatsForEveryCountAndSize() {
        for (count in 1..6) for ((w, h) in listOf(110f to 110f, 176f to 222f, 272f to 223f, 320f to 330f)) overview(count, w, h)
    }

    @Test fun overviewKeepsAllTilesAfterARecomposition() = runGlanceAppWidgetUnitTest(timeout = 2.minutes) {
        setContext(context)
        setAppWidgetSize(selected(WidgetKind.Overview, 176f, 222f))
        // The widget recomposes whenever its data or configuration flow emits (the first emission is often a default). Every pass must compose every tile.
        val tick = androidx.compose.runtime.mutableIntStateOf(0)
        provideComposable {
            val cfg = WidgetConfig(corners = if (tick.intValue % 2 == 0) Corners.System else Corners.ExtraRound).resolve(WidgetKind.Overview, AppAppearance())
            OverviewWidget().RenderSample(cfg)
        }
        for (pass in 0..3) {
            for (label in listOf("Steps", "Sleep", "Recovery", "Resting HR")) onAllNodes(hasText(label) or hasText(Layouts.shortLabel(label))).assertCountEquals(1)
            tick.intValue++
        }
    }
}
