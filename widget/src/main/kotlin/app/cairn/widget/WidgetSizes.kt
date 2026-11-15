package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The Responsive size set of each widget (at most 16: the RemoteViews limit), chosen from that widget's OWN layout thresholds
 * rather than a global grid: a size point sits where the rules add a row, tile or column, so the size the launcher selects
 * (the largest one that fits the real cell) holds everything the real cell has room for.
 *
 * Overview's list is placed at its own thresholds: a tile needs 56 x 52 dp, so a second column opens at an effective 146 dp of width,
 * a third at 208, a fourth at 270, and extra rows at 162, 220 and 278 dp of height; each is registered MARGIN_DP lower.
 *
 * The other lists were produced by the greedy search in `WidgetSizesTest.generate` (run with GEN_SIZES=1) and are checked by
 * `WidgetSizesTest` against typical launcher cells. Re-run the generator after changing a layout rule.
 */
object WidgetSizes {
    val byKind: Map<WidgetKind, List<Pair<Int, Int>>> = mapOf(
        WidgetKind.Today to listOf(110 to 110, 110 to 125, 110 to 135, 110 to 145, 110 to 155, 110 to 175, 110 to 195, 195 to 110, 200 to 110, 320 to 110, 200 to 130, 200 to 165, 200 to 195, 200 to 215, 260 to 235, 320 to 240),
        WidgetKind.Recovery to listOf(110 to 110, 110 to 130, 110 to 150, 110 to 155, 120 to 130, 120 to 155, 195 to 110, 195 to 130, 195 to 140, 195 to 160),
        WidgetKind.LastActivity to listOf(250 to 40, 250 to 55, 250 to 85, 250 to 95, 250 to 110, 250 to 115, 250 to 140, 250 to 160, 280 to 55, 325 to 85, 325 to 100, 325 to 115, 325 to 140),
        WidgetKind.TrainingLoad to listOf(250 to 110, 250 to 135),
        WidgetKind.Sleep to listOf(110 to 110, 110 to 125, 110 to 135, 110 to 145, 110 to 165, 110 to 200, 110 to 215, 115 to 185, 115 to 200, 195 to 110, 195 to 130, 195 to 140, 195 to 170, 215 to 145, 230 to 110, 235 to 130),
        WidgetKind.Overview to listOf(110 to 110, 110 to 154, 110 to 212, 110 to 270, 138 to 110, 138 to 154, 138 to 212, 138 to 270, 200 to 110, 200 to 154, 200 to 212, 200 to 270, 262 to 110, 262 to 154, 262 to 212, 262 to 270),
        WidgetKind.Fitness to listOf(250 to 110, 250 to 120, 250 to 140, 250 to 170, 250 to 185, 250 to 200, 250 to 235, 285 to 110, 285 to 115, 285 to 120, 285 to 140),
    )

    /**
     * Launchers report a widget a little smaller than its cell (padding, rounding, the portrait/landscape min and max rule), so
     * each size is registered this much below the size its layout rule is evaluated at. A variant is picked as early as is safe,
     * and the rule is given declared size + MARGIN_DP, which the real cell always reaches within the same tolerance.
     */
    const val MARGIN_DP = 8f

    fun dp(kind: WidgetKind): Set<DpSize> = byKind.getValue(kind).map { DpSize(it.first.dp, it.second.dp) }.toSet()

    /** What Android does with a map of sized layouts: the largest-area size that fits the cell, else the smallest. */
    fun pick(sizes: List<Pair<Int, Int>>, w: Float, h: Float): Pair<Int, Int> {
        val fitting = sizes.filter { it.first <= w && it.second <= h }
        return if (fitting.isNotEmpty()) fitting.maxBy { it.first * it.second } else sizes.minBy { it.first * it.second }
    }
}
