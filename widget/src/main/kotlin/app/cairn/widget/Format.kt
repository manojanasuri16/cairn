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

/** Layout decisions that depend on size and font scale; pure so they can be tested. */
object SizeRules {
    /** Shrinks a constrained text size so that, after the system font scale, it never exceeds [cap] x [baseSp]. */
    fun cappedSp(baseSp: Float, fontScale: Float, cap: Float = 1.3f): Float =
        if (fontScale > cap) baseSp * cap / fontScale else baseSp

    /** How many metric rows fit in [heightDp] at [fontScale]; always at least one. */
    fun visibleRows(heightDp: Float, fontScale: Float, rowDp: Float = 22f): Int =
        (heightDp / (rowDp * fontScale.coerceAtLeast(1f))).toInt().coerceAtLeast(1)

    // Vertical space every widget reserves: frame padding (top and bottom) plus the footer line.
    private const val PAD_DP = 20f
    private const val FOOTER_DP = 22f

    private fun avail(heightDp: Float, scale: Float) = heightDp - PAD_DP - FOOTER_DP * scale.coerceAtLeast(1f)

    enum class TodayMode { RingOnly, Stacked, Side }

    /** [ringDp] is the ring diameter, [rows] how many metric rows fit (0 for [TodayMode.RingOnly]). */
    data class TodayLayout(val mode: TodayMode, val ringDp: Int, val rows: Int)

    /**
     * Today widget layout from the real size Glance reports ([SizeMode.Exact]).
     * Narrow cells stack the ring over metric rows, wide cells put them side by side.
     * Rows are added as height allows, in priority order (sleep, resting HR, recovery, ...), at most [MAX_TODAY_ROWS].
     */
    fun today(widthDp: Float, heightDp: Float, fontScale: Float): TodayLayout {
        val s = fontScale.coerceAtLeast(1f)
        val rowDp = 20f * s
        return if (widthDp >= 180f) {
            val ring = if (heightDp >= 150f) 92 else if (heightDp >= 100f) 68 else 52
            val rows = (avail(heightDp, s) / rowDp).toInt().coerceIn(1, MAX_TODAY_ROWS)
            TodayLayout(TodayMode.Side, ring, rows)
        } else {
            val ring = if (heightDp >= 130f) 60 else 72
            val rows = if (widthDp < 110f) 0 else ((avail(heightDp, s) - ring - 6f) / rowDp).toInt().coerceIn(0, MAX_TODAY_ROWS)
            if (rows == 0) TodayLayout(TodayMode.RingOnly, 72, 0) else TodayLayout(TodayMode.Stacked, ring, rows)
        }
    }

    const val MAX_TODAY_ROWS = 4

    /** [wide]: score and window sit beside the duration; otherwise they stack below it. */
    data class SleepLayout(val wide: Boolean, val score: Boolean, val window: Boolean, val bar: Boolean, val legend: Boolean, val note: Boolean)

    /** Sleep: duration is the hero; score, stage bar, window, legend and note appear in that order as height allows. */
    fun sleep(widthDp: Float, heightDp: Float, fontScale: Float): SleepLayout {
        val s = fontScale.coerceAtLeast(1f)
        val wide = widthDp >= 200f
        var left = avail(heightDp, s) - 40f * s // label and duration
        fun take(cost: Float): Boolean = (left >= cost).also { if (it) left -= cost }
        if (wide) left += 14f * s // no separate label row
        val score = if (wide) true else take(16f * s)
        val bar = take(14f)
        val window = if (wide) true else take(16f * s)
        val legend = bar && take(14f * s)
        val note = legend && take(12f * s)
        return SleepLayout(wide, score, window, bar, legend, note)
    }

    enum class ActivityMode { OneLine, Card }

    /** [statsPerRow] stats (duration, pace, heart rate) per line, [statRows] lines that fit. */
    data class ActivityLayout(val mode: ActivityMode, val statsPerRow: Int, val statRows: Int) {
        val maxStats: Int get() = statsPerRow * statRows
    }

    /** Last activity: one line when short, otherwise sport/date, distance hero and as many stats as fit. */
    fun lastActivity(widthDp: Float, heightDp: Float, fontScale: Float): ActivityLayout {
        val s = fontScale.coerceAtLeast(1f)
        if (heightDp < 80f) return ActivityLayout(ActivityMode.OneLine, 0, 0)
        val perRow = if (widthDp >= 220f) 3 else 2
        val rows = ((avail(heightDp, s) - 48f * s) / (18f * s)).toInt().coerceIn(0, 2)
        return ActivityLayout(ActivityMode.Card, perRow, rows)
    }
}
