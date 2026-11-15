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
