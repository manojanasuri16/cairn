package app.cairn.widget

import kotlin.math.max
import kotlin.math.min

/**
 * Pure text-width estimates so layouts choose columns, sizes and fallbacks before a VALUE could ever be ellipsized.
 * Widths are deliberately a little generous (real glyphs are narrower on average).
 */
object TextFit {
    private fun glyph(c: Char): Float = when {
        c.isDigit() -> 0.60f
        c == '.' || c == ',' || c == ':' || c == ' ' || c == '/' || c == '·' || c == '\'' -> 0.32f
        c == '%' -> 0.85f
        c == 'm' || c == 'w' -> 0.88f
        c == 'M' || c == 'W' -> 0.95f
        c.isUpperCase() -> 0.68f
        c == 'i' || c == 'l' || c == 't' || c == 'f' || c == 'j' || c == 'r' -> 0.40f
        else -> 0.58f
    }

    /** How much the system font scale really enlarges text drawn at [sp] (it is capped by `Txt`). */
    fun effectiveScale(fontScale: Float, cap: Float = Layouts.TEXT_CAP): Float = min(max(fontScale, 1f), cap)

    /** Estimated rendered width in dp of [text] at [sp] under [fontScale]. */
    fun widthDp(text: String, sp: Float, fontScale: Float, cap: Float = Layouts.TEXT_CAP): Float =
        text.sumOf { glyph(it).toDouble() }.toFloat() * sp * effectiveScale(fontScale, cap) + 2f

    fun fits(text: String, sp: Float, fontScale: Float, availDp: Float, cap: Float = Layouts.TEXT_CAP): Boolean =
        widthDp(text, sp, fontScale, cap) <= availDp

    /** Smallest sp that still renders at [minEffectiveDp] after the system font scale. */
    fun minSp(fontScale: Float, minEffectiveDp: Float = 12f, cap: Float = Layouts.TEXT_CAP): Float =
        max(8f, minEffectiveDp / effectiveScale(fontScale, cap))

    /** Largest sp in [floorSp]..[startSp] (1 sp steps) at which [text] fits [availDp]; [floorSp] when none does. */
    fun fitSp(text: String, startSp: Float, floorSp: Float, availDp: Float, fontScale: Float, cap: Float = Layouts.TEXT_CAP): Float {
        var sp = startSp
        while (sp > floorSp && !fits(text, sp, fontScale, availDp, cap)) sp -= 1f
        return max(sp, min(floorSp, startSp))
    }

    /** Splits [texts] into lines so each line (entries plus [extraPerEntry] dp each, [gap] between) fits [availDp]. At least one per line. */
    fun chunkByWidth(texts: List<String>, sp: Float, fontScale: Float, availDp: Float, extraPerEntry: Float = 0f, gap: Float = 6f): List<List<String>> {
        val lines = mutableListOf<MutableList<String>>()
        var used = 0f
        for (t in texts) {
            val w = widthDp(t, sp, fontScale) + extraPerEntry
            if (lines.isEmpty() || used + gap + w > availDp) { lines += mutableListOf(t); used = w } else { lines.last() += t; used += gap + w }
        }
        return lines
    }
}
