package app.cairn.core.designsystem

import kotlin.math.ceil

/** Pure helpers for fitting values into boxes: choose columns, a value size, and balanced row widths. Shared by the app and the widgets. */
object FitMath {
    /** Most columns (up to [maxCols]) whose tiles are each at least as wide as the widest of [needs]; always at least 1. */
    fun columns(available: Float, needs: List<Float>, gap: Float, maxCols: Int): Int {
        if (needs.isEmpty()) return maxCols.coerceAtLeast(1)
        val widest = needs.max()
        for (c in maxCols downTo 2) if (widest * c + gap * (c - 1) <= available) return c
        return 1
    }

    /** Largest size in [floor]..[max] (stepping down by [step]) for which [widthAt] fits [available]; [floor] when none does. */
    fun valueSize(widthAt: (Float) -> Float, available: Float, max: Float, floor: Float, step: Float = 1f): Float {
        var s = max
        while (s > floor && widthAt(s) > available) s -= step
        return s.coerceAtLeast(floor)
    }

    /** Tiles per row for [n] tiles in at most [cols] columns, balanced: no orphan unless unavoidable (3 in 2 columns gives 2 then 1; that last tile spans the full width). */
    fun rows(n: Int, cols: Int): List<Int> {
        if (n <= 0) return emptyList()
        val c = cols.coerceAtLeast(1)
        val rowCount = ceil(n / c.toFloat()).toInt()
        val base = n / rowCount
        val extra = n % rowCount
        return List(rowCount) { base + if (it < extra) 1 else 0 }
    }

    /** Widths for one row: each tile gets its [needs] width, the remaining width of [total] is shared evenly, so the row fills edge to edge. A lone tile spans [total]. */
    fun balancedWidths(needs: List<Float>, total: Float, gap: Float): List<Float> {
        if (needs.isEmpty()) return emptyList()
        val usable = total - gap * (needs.size - 1)
        val extra = ((usable - needs.sum()) / needs.size).coerceAtLeast(0f)
        return needs.map { it + extra }
    }
}
