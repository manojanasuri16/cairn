package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The Responsive size set of each widget (at most 16: the RemoteViews limit), chosen from that widget's OWN layout thresholds
 * rather than a global grid: a size point sits where the rules add a row, tile or column, so the size the launcher selects
 * (the largest one that fits the real cell) holds everything the real cell has room for.
 *
 * The lists were produced by the greedy search in `WidgetSizesTest.generate` (run with GEN_SIZES=1) and are checked by
 * `WidgetSizesTest` against typical launcher cells. Re-run the generator after changing a layout rule.
 */
object WidgetSizes {
    val byKind: Map<WidgetKind, List<Pair<Int, Int>>> = mapOf(
        WidgetKind.Today to listOf(110 to 110, 110 to 130, 110 to 145, 110 to 165, 110 to 170, 110 to 185, 110 to 205, 200 to 110, 200 to 115, 200 to 135, 200 to 175, 270 to 110, 200 to 215, 250 to 130, 250 to 150, 270 to 230),
        WidgetKind.Recovery to listOf(110 to 110, 110 to 140, 110 to 160, 125 to 130, 125 to 140, 125 to 150, 125 to 160, 200 to 110, 200 to 130, 200 to 140, 200 to 150, 200 to 165),
        WidgetKind.LastActivity to listOf(250 to 40, 250 to 70, 250 to 85, 250 to 90, 250 to 100, 250 to 115, 250 to 120, 250 to 150, 250 to 170),
        WidgetKind.TrainingLoad to listOf(250 to 110, 250 to 145),
        WidgetKind.Sleep to listOf(110 to 110, 110 to 130, 110 to 220, 110 to 170, 110 to 200, 130 to 220, 130 to 185, 130 to 200, 200 to 110, 200 to 135, 200 to 150, 200 to 175, 235 to 110, 235 to 115, 235 to 135, 235 to 145),
        WidgetKind.Overview to listOf(110 to 110, 110 to 170, 110 to 250, 170 to 110, 170 to 170, 170 to 250, 250 to 110, 250 to 170, 250 to 250, 330 to 110, 330 to 170, 330 to 250),
        WidgetKind.Fitness to listOf(250 to 110, 250 to 125, 250 to 145, 250 to 150, 250 to 170, 250 to 175, 250 to 190, 250 to 210, 250 to 230, 300 to 110, 300 to 125, 300 to 145, 300 to 150, 300 to 175, 300 to 210, 300 to 240),
    )

    fun dp(kind: WidgetKind): Set<DpSize> = byKind.getValue(kind).map { DpSize(it.first.dp, it.second.dp) }.toSet()

    /** What Android does with a map of sized layouts: the largest-area size that fits the cell, else the smallest. */
    fun pick(sizes: List<Pair<Int, Int>>, w: Float, h: Float): Pair<Int, Int> {
        val fitting = sizes.filter { it.first <= w && it.second <= h }
        return if (fitting.isNotEmpty()) fitting.maxBy { it.first * it.second } else sizes.minBy { it.first * it.second }
    }
}
