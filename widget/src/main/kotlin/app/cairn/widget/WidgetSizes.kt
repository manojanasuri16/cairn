package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The Responsive size set of each widget (at most 16: the RemoteViews limit), chosen from that widget's OWN layout thresholds
 * rather than a global grid: a size point sits where the rules add a row, tile or column, so the size the launcher selects
 * (the largest one that fits the real cell) holds everything the real cell has room for.
 *
 * Overview's list is placed at its own thresholds: a tile needs 56 x 52 dp, so a second column opens at 138 dp of width,
 * a third at 200, a fourth at 262, and extra rows at 170, 228 and 286 dp of height (the 40 dp footer holds the 40 x 40 dp sync button).
 *
 * The other lists were produced by the greedy search in `WidgetSizesTest.generate` (run with GEN_SIZES=1) and are checked by
 * `WidgetSizesTest` against typical launcher cells. Re-run the generator after changing a layout rule.
 */
object WidgetSizes {
    val byKind: Map<WidgetKind, List<Pair<Int, Int>>> = mapOf(
        WidgetKind.Today to listOf(110 to 110, 110 to 160, 110 to 180, 110 to 200, 110 to 220, 200 to 110, 200 to 120, 200 to 150, 200 to 190, 200 to 210, 200 to 230, 210 to 150, 210 to 155, 210 to 235, 260 to 110, 260 to 120),
        WidgetKind.Recovery to listOf(110 to 110, 110 to 155, 110 to 175, 125 to 145, 125 to 155, 125 to 165, 125 to 175, 125 to 180, 200 to 110, 200 to 145, 200 to 155, 200 to 165, 200 to 180),
        WidgetKind.LastActivity to listOf(110 to 110, 110 to 115, 110 to 145, 110 to 185, 110 to 230, 165 to 185, 170 to 125, 170 to 160, 175 to 110, 180 to 185, 220 to 110, 220 to 120, 220 to 165, 220 to 185, 250 to 40, 250 to 90),
        WidgetKind.TrainingLoad to listOf(110 to 110, 110 to 160, 110 to 165, 110 to 175, 110 to 180, 200 to 110, 200 to 115, 200 to 160),
        WidgetKind.Sleep to listOf(110 to 110, 110 to 120, 110 to 145, 110 to 160, 110 to 165, 110 to 180, 110 to 225, 110 to 240, 200 to 110, 200 to 120, 200 to 155, 200 to 170, 200 to 185, 200 to 190, 200 to 195),
        WidgetKind.Overview to listOf(110 to 110, 110 to 178, 110 to 236, 110 to 294, 146 to 110, 146 to 178, 146 to 236, 146 to 294, 208 to 110, 208 to 178, 208 to 236, 208 to 294, 270 to 110, 270 to 178, 270 to 236, 270 to 294),
        WidgetKind.Hrv to listOf(110 to 110, 110 to 145, 110 to 155, 110 to 175, 110 to 180, 110 to 190, 110 to 200, 110 to 205, 110 to 215, 200 to 110, 200 to 120, 200 to 140, 200 to 145, 200 to 155, 200 to 185, 200 to 210),
        WidgetKind.HeartRate to listOf(110 to 110, 110 to 140, 110 to 145, 110 to 150, 110 to 155, 110 to 165, 110 to 170, 110 to 180, 140 to 110, 200 to 110, 200 to 115, 200 to 145),
        WidgetKind.Stress to listOf(110 to 110, 110 to 150, 110 to 155, 110 to 180, 110 to 205, 110 to 225, 110 to 240, 200 to 110, 200 to 140, 200 to 150, 200 to 155, 200 to 180),
        WidgetKind.WeeklyMileage to listOf(110 to 110, 110 to 115, 110 to 145, 110 to 160, 110 to 175, 110 to 180, 110 to 195, 200 to 110, 200 to 115, 200 to 120, 200 to 140),
        WidgetKind.Fitness to listOf(110 to 110, 110 to 140, 110 to 165, 110 to 190, 110 to 210, 110 to 255, 110 to 305, 155 to 110, 165 to 140, 165 to 160, 165 to 210, 165 to 240, 175 to 190, 300 to 110, 310 to 140, 310 to 160),
    )

    /**
     * Extra dp the layout rule is given beyond the declared size. It is 0: the cell a launcher really gives a widget can equal the declared
     * size, so a rule evaluated above it overflows (the 2x2 cut-offs found by the widget matrix). A cell that is reported a little under its
     * true size simply shows the layout one step down, never a cut one.
     */
    const val MARGIN_DP = 0f

    fun dp(kind: WidgetKind): Set<DpSize> = byKind.getValue(kind).map { DpSize(it.first.dp, it.second.dp) }.toSet()

    /** What Android does with a map of sized layouts: the largest-area size that fits the cell, else the smallest. */
    fun pick(sizes: List<Pair<Int, Int>>, w: Float, h: Float): Pair<Int, Int> {
        val fitting = sizes.filter { it.first <= w && it.second <= h }
        return if (fitting.isNotEmpty()) fitting.maxBy { it.first * it.second } else sizes.minBy { it.first * it.second }
    }
}
