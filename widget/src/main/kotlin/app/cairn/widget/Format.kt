package app.cairn.widget

import app.cairn.core.model.DistanceUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    fun distance(meters: Double, unit: DistanceUnit, locale: Locale = Locale.getDefault()): String = when (unit) {
        DistanceUnit.Km -> distanceKm(meters, locale)
        DistanceUnit.Mi -> String.format(locale, "%.1f mi", meters / METERS_PER_MILE)
    }

    /** "5:13 /km", or "8:24 /mi" when [unit] is miles. */
    fun pace(secPerKm: Int, unit: DistanceUnit = DistanceUnit.Km): String {
        val sec = if (unit == DistanceUnit.Mi) Math.round(secPerKm * METERS_PER_MILE / 1000.0).toInt() else secPerKm
        return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')} " + if (unit == DistanceUnit.Mi) "/mi" else "/km"
    }

    /** Race time: "24:10", "1:52:03". */
    fun raceTime(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }

    /** "00:28\u201308:30" in 24-hour time. */
    fun window(start: Instant, end: Instant, zone: ZoneId): String {
        val f = DateTimeFormatter.ofPattern("HH:mm")
        return f.format(start.atZone(zone)) + "\u2013" + f.format(end.atZone(zone))
    }

    fun windowSpoken(start: Instant, end: Instant, zone: ZoneId): String {
        val f = DateTimeFormatter.ofPattern("HH:mm")
        return f.format(start.atZone(zone)) + " to " + f.format(end.atZone(zone))
    }

    /** "+1", "-2", "steady". */
    fun trend(delta: Int): String = when {
        delta > 0 -> "+$delta"
        delta < 0 -> "-${-delta}"
        else -> "steady"
    }

    fun trendSpoken(t: String): String = when {
        t.startsWith("+") -> "up ${t.drop(1)}"
        t.startsWith("-") -> "down ${t.drop(1)}"
        else -> t
    }

    const val METERS_PER_MILE = 1609.344

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

/** Small helpers shared by the size rules. */
object SizeRules {
    /** Shrinks a constrained text size so that, after the system font scale, it never exceeds [cap] x [baseSp]. */
    fun cappedSp(baseSp: Float, fontScale: Float, cap: Float = 1.3f): Float =
        if (fontScale > cap) baseSp * cap / fontScale else baseSp

    /** How many metric rows fit in [heightDp] at [fontScale]; always at least one. */
    fun visibleRows(heightDp: Float, fontScale: Float, rowDp: Float = 22f): Int =
        (heightDp / (rowDp * fontScale.coerceAtLeast(1f))).toInt().coerceAtLeast(1)
}
