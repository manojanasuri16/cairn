package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The Responsive size set of each widget (at most 16: the RemoteViews limit), chosen from that widget's OWN layout thresholds
 * rather than a global grid: a size point sits where the rules add a row, tile or column, so the size the launcher selects
 * (the largest one that fits the real cell) holds everything the real cell has room for.
 *
 * Overview's list is placed at its own thresholds: a tile needs 56 x 52 dp, so a second column opens at an effective 146 dp of width,
 * a third at 208, a fourth at 270, and extra rows at 166, 224 and 282 dp (the 28 dp footer holds the 40 dp wide sync button) of height; each is registered MARGIN_DP lower.
 *
 * The other lists were produced by the greedy search in `WidgetSizesTest.generate` (run with GEN_SIZES=1) and are checked by
 * `WidgetSizesTest` against typical launcher cells. Re-run the generator after changing a layout rule.
 */
object WidgetSizes {
    val byKind: Map<WidgetKind, List<Pair<Int, Int>>> = mapOf(
        WidgetKind.Today to listOf(110 to 110, 110 to 130, 110 to 145, 110 to 165, 110 to 185, 110 to 200, 195 to 110, 200 to 110, 200 to 130, 200 to 135, 200 to 170, 200 to 195, 200 to 215, 260 to 235, 320 to 110, 320 to 245),
        WidgetKind.Recovery to listOf(110 to 110, 110 to 140, 110 to 160, 120 to 130, 120 to 145, 120 to 165, 195 to 110, 195 to 130, 195 to 145, 195 to 165),
        WidgetKind.LastActivity to listOf(250 to 40, 250 to 65, 250 to 85, 250 to 100, 250 to 120, 250 to 145, 250 to 165, 305 to 65),
        WidgetKind.TrainingLoad to listOf(250 to 110, 250 to 145),
        WidgetKind.Sleep to listOf(110 to 110, 110 to 115, 110 to 125, 110 to 140, 110 to 150, 110 to 165, 110 to 205, 110 to 220, 120 to 190, 120 to 205, 195 to 110, 195 to 115, 195 to 130, 195 to 145, 195 to 170, 245 to 115),
        WidgetKind.Overview to listOf(110 to 110, 110 to 158, 110 to 216, 110 to 274, 138 to 110, 138 to 158, 138 to 216, 138 to 274, 200 to 110, 200 to 158, 200 to 216, 200 to 274, 262 to 110, 262 to 158, 262 to 216, 262 to 274),
        WidgetKind.Hrv to listOf(110 to 110, 110 to 140, 110 to 145, 110 to 160, 110 to 170, 110 to 185, 110 to 195, 195 to 110, 195 to 125, 195 to 135, 195 to 165, 195 to 190),
        WidgetKind.HeartRate to listOf(110 to 110, 110 to 120, 110 to 130, 110 to 145, 110 to 160, 195 to 110, 195 to 125),
        WidgetKind.Stress to listOf(110 to 110, 110 to 135, 110 to 160, 110 to 185, 110 to 205, 110 to 220, 195 to 110, 195 to 120, 195 to 135, 195 to 160),
        WidgetKind.WeeklyMileage to listOf(110 to 110, 110 to 125, 110 to 145, 110 to 160, 110 to 175, 195 to 110, 195 to 120),
        WidgetKind.Fitness to listOf(250 to 110, 250 to 125, 250 to 145, 250 to 150, 250 to 175, 250 to 190, 250 to 205, 250 to 240, 305 to 110, 305 to 120, 305 to 125, 305 to 150, 320 to 150),
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
