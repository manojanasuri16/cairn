package app.cairn.widget

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting. Units sit next to the number ("7.2 km", "5:13 /km", "8h 13m"). */
object Fmt {
    fun steps(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)

    /** "8h 13m", "45m", "8h". */
    fun duration(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "${r}m"
            r == 0 -> "${h}h"
            else -> "${h}h ${r}m"
        }
    }

    fun durationSec(sec: Int): String = duration((sec + 30) / 60)

    fun distanceKm(meters: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f km", meters / 1000.0)

    fun pace(secPerKm: Int): String = "${secPerKm / 60}:${(secPerKm % 60).toString().padStart(2, '0')} /km"

    fun bpm(v: Int): String = "$v bpm"

    fun date(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }

    /** "RUNNING" / "trail_run" -> "Running" / "Trail run". */
    fun sport(raw: String): String {
        val s = raw.replace('_', ' ').replace('-', ' ').trim().lowercase()
        return if (s.isEmpty()) "Activity" else s.replaceFirstChar { it.titlecase() }
    }

    /** "Full in 6h", "Full in 1d 4h", null when already full or unknown. */
    fun timeToFull(hours: Int?): String? {
        if (hours == null || hours <= 0) return null
        return if (hours >= 24) "Full in ${hours / 24}d" + (hours % 24).let { if (it == 0) "" else " ${it}h" } else "Full in ${hours}h"
    }
}

/** Layout decisions that depend on size and font scale; pure so they can be tested. */
object SizeRules {
    /** Shrinks a constrained text size so that, after the system font scale, it never exceeds [cap] x [baseSp]. */
    fun cappedSp(baseSp: Float, fontScale: Float, cap: Float = 1.3f): Float =
        if (fontScale > cap) baseSp * cap / fontScale else baseSp

    /** How many metric rows fit in [heightDp] at [fontScale]; always at least one. */
    fun visibleRows(heightDp: Float, fontScale: Float, rowDp: Float = 22f): Int =
        (heightDp / (rowDp * fontScale.coerceAtLeast(1f))).toInt().coerceAtLeast(1)
}
