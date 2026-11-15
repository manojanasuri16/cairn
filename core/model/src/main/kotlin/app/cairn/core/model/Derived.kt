package app.cairn.core.model

import java.time.LocalDate

/** Numbers COROS shows that are derived from what the server returns. Pure, shared by the app and the widgets. */
object Derived {
    /** COROS "Intensity Trend" is the load ratio as a percent: 0.47 -> 47. */
    fun intensityTrendPercent(loadRatio: Double): Int = Math.round(loadRatio * 100.0).toInt()

    /** Days of the rolling window behind the "7-Day Training Load" figure, ending [today]. */
    const val LOAD_WINDOW_DAYS = 7
}

/**
 * COROS "7-Day Training Load": the sum of the training loads of the activities of the last 7 days (today and the six days before),
 * plus the Monday-to-Sunday bars of the current week. [missing] counts activities whose load is not cached yet.
 */
data class WeeklyLoad(
    val total: Int,
    /** Monday..Sunday of the current week; null for days after today and for days without a known load of 0. */
    val weekBars: List<Int?>,
    val missing: Int,
    val activityCount: Int,
) {
    companion object {
        fun compute(activities: List<Activity>, details: Map<String, ActivityDetail>, today: LocalDate): WeeklyLoad {
            val from = today.minusDays((Derived.LOAD_WINDOW_DAYS - 1).toLong())
            val inWindow = activities.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            fun load(a: Activity) = details[a.id]?.trainingLoad
            val total = inWindow.sumOf { load(it) ?: 0 }
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val bars = (0..6).map { i ->
                val d = monday.plusDays(i.toLong())
                if (d.isAfter(today)) null else activities.filter { it.date == d }.sumOf { load(it) ?: 0 }
            }
            return WeeklyLoad(total, bars, inWindow.count { load(it) == null }, inWindow.size)
        }
    }
}

/** COROS wording for the sleep score band. The thresholds are an assumption: COROS does not publish them. */
object SleepRating {
    fun word(score: Int): String = when {
        score >= 80 -> "Optimal"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Poor"
    }
}

/**
 * COROS-style stress words for the day's average stress (1-99). **The band edges are an assumption**: COROS does not publish them,
 * so Cairn uses Relaxed 1-25, Low 26-50, Medium 51-75 and High 76-99 (documented in docs/data-sources.md).
 */
enum class StressBand(val word: String, val low: Int, val high: Int) {
    Relaxed("Relaxed", 1, 25), Low("Low", 26, 50), Medium("Medium", 51, 75), High("High", 76, 99);

    companion object {
        /** Null for 0 or less: the server uses "no data" there. Anything above 99 is High. */
        fun of(value: Int): StressBand? = when {
            value <= 0 -> null
            value <= 25 -> Relaxed
            value <= 50 -> Low
            value <= 75 -> Medium
            else -> High
        }
    }
}

/**
 * COROS-style weekly distance: the sum of `distanceMeters` of ALL cached activities from Monday to Sunday of the week that holds
 * [today] (the same way COROS's month header sums), the per-day sums, and the previous Monday-Sunday week for the comparison.
 * A week that spans two months is handled because dates, not months, decide.
 */
data class WeeklyMileage(
    val monday: LocalDate,
    val totalMeters: Double,
    val count: Int,
    /** Monday..Sunday of this week; days after today are 0. */
    val dayMeters: List<Double>,
    val lastWeekMeters: Double,
    val lastWeekCount: Int,
) {
    /** Percent change against last week, or null when last week has no distance to compare with. */
    val changePercent: Int? get() = if (lastWeekMeters > 0.0) Math.round((totalMeters - lastWeekMeters) / lastWeekMeters * 100.0).toInt() else null

    companion object {
        fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

        fun compute(activities: List<Activity>, today: LocalDate): WeeklyMileage {
            val monday = mondayOf(today)
            val lastMonday = monday.minusDays(7)
            fun dist(a: Activity) = (a.distanceMeters ?: 0.0).coerceAtLeast(0.0)
            val thisWeek = activities.filter { !it.date.isBefore(monday) && !it.date.isAfter(monday.plusDays(6)) && !it.date.isAfter(today) }
            val lastWeek = activities.filter { !it.date.isBefore(lastMonday) && it.date.isBefore(monday) }
            val days = (0..6).map { i -> val d = monday.plusDays(i.toLong()); thisWeek.filter { it.date == d }.sumOf(::dist) }
            return WeeklyMileage(monday, thisWeek.sumOf(::dist), thisWeek.size, days, lastWeek.sumOf(::dist), lastWeek.size)
        }
    }
}
