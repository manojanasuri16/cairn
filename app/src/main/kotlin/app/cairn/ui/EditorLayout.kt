package app.cairn.ui

/** Pure sizing rules for the widget editor's pinned preview (unit-tested). All values in dp. */
object EditorLayout {
    /** At or above this width the preview goes to the left and the options to the right. */
    const val SIDE_BY_SIDE_MIN_WIDTH = 600f
    const val SIDE_PREVIEW_FRACTION = 0.4f

    fun sideBySide(widthDp: Float): Boolean = widthDp >= SIDE_BY_SIDE_MIN_WIDTH

    /** Tallest the stacked preview area may be: 35% of the window (30% on short windows), never below a readable minimum. */
    fun previewMaxHeightDp(windowHeightDp: Float): Float =
        if (windowHeightDp >= 560f) maxOf(windowHeightDp * 0.35f, 120f) else maxOf(windowHeightDp * 0.30f, 104f)

    /** Uniform scale that fits w x h into maxW x maxH; never above 1 (no upscaling) and never non-positive. */
    fun scale(w: Float, h: Float, maxW: Float, maxH: Float): Float {
        if (w <= 0f || h <= 0f) return 1f
        return minOf(1f, maxW / w, maxH / h).coerceAtLeast(0.05f)
    }
}
