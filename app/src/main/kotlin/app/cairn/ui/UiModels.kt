package app.cairn.ui

import androidx.compose.runtime.Immutable
import app.cairn.widget.FooterUi

/** Immutable UI state. Built by the pure mappers in [Mappers.kt] from domain types; screens only draw it. */

@Immutable
data class RingUi(val fraction: Float, val valueText: String, val caption: String, val description: String)

enum class StageKind { Deep, Light, Rem, Awake }

@Immutable
data class StageUi(val kind: StageKind, val label: String, val minutes: Int, val text: String)

@Immutable
data class SleepUi(
    val durationText: String,
    val scoreText: String?,
    /** COROS rating word for the score (Optimal / Good / Fair / Poor); the thresholds are an assumption. */
    val rating: String? = null,
    val window: String?,
    val stages: List<StageUi>,
    val description: String,
)

@Immutable
data class MetricUi(val label: String, val value: String)

@Immutable
data class SparkUi(val title: String, val values: List<Float?>, val latest: String?, val description: String)

/** What to show instead of content, and where the action leads. */
enum class EmptyAction { None, OpenAccount }

@Immutable
data class EmptyUi(val title: String, val hint: String, val action: EmptyAction = EmptyAction.None)

/** One figure of the header row: "9,443" "steps". */
@Immutable
data class StatUi(val value: String, val label: String)

@Immutable
data class TodayActivityUi(
    val id: String, val kind: SportKind, val sportLabel: String, val title: String, val date: String,
    val distance: String?, val duration: String?, val pace: String?,
    /** Distance, or the duration when the workout has none. */
    val hero: String?, val secondary: List<String>, val description: String,
)

@Immutable
data class HeartUi(val value: String, val unit: String, val caption: String, val range: String?, val description: String)

@Immutable
data class RestingUi(val value: String, val unit: String, val time: String?, val average: String?, val description: String)

@Immutable
data class StressUi(val value: String, val caption: String, val description: String)

@Immutable
data class HrvUi(val assessment: String?, val average: String, val range: String?, val baseline: String?, val description: String)

@Immutable
data class WeekBarUi(val letter: String, val fraction: Float, val isToday: Boolean)

@Immutable
data class WeeklyLoadUi(val total: String, val suggested: String?, val bars: List<WeekBarUi>, val note: String?, val description: String)

/** COROS "Training Status": status word and the four load figures. */
@Immutable
data class TrainingStatusUi(
    val status: String?,
    val sevenDayLoad: String?,
    val loadImpact: String?,
    val baseFitness: String?,
    val intensityTrend: String?,
    val description: String,
)

@Immutable
data class RunningFitnessUi(val value: String, val fraction: Float, val marathon: String?, val description: String)

@Immutable
data class TodayState(
    val loading: Boolean = true,
    /** Header row in COROS order: steps, kcal, min. */
    val header: List<StatUi> = emptyList(),
    val todayActivity: TodayActivityUi? = null,
    val heart: HeartUi? = null,
    val resting: RestingUi? = null,
    val stress: StressUi? = null,
    val sleep: SleepUi? = null,
    val hrv: HrvUi? = null,
    val weekly: WeeklyLoadUi? = null,
    val status: TrainingStatusUi? = null,
    val recovery: RingUi? = null,
    val recoveryLevel: String? = null,
    val timeToFull: String? = null,
    val running: RunningFitnessUi? = null,
    val sparks: List<SparkUi> = emptyList(),
    val footer: FooterUi? = null,
    val empty: EmptyUi? = null,
)

@Immutable
data class ActivityRowUi(
    val id: String,
    val kind: SportKind,
    val sport: String,
    val date: String,
    /** "Sunday  Riverside Run": weekday, then the place and sport. */
    val title: String = sport,
    val distance: String? = null,
    val duration: String? = null,
    val pace: String? = null,
    /** Distance, duration, pace, heart rate, calories: only those that exist, in that order. */
    val stats: List<String>,
    val details: List<MetricUi>,
    val description: String,
    val hero: String? = null,
    val secondary: List<String> = emptyList(),
)

@Immutable
data class MonthUi(val title: String, val distance: String, val count: String, val hours: String, val rows: List<ActivityRowUi>)

@Immutable
data class ActivitiesState(
    val loading: Boolean = true,
    val rows: List<ActivityRowUi> = emptyList(),
    /** The same rows grouped by month, newest month first, like COROS "All activities". */
    val months: List<MonthUi> = emptyList(),
    val footer: FooterUi? = null,
    val empty: EmptyUi? = null,
)

@Immutable
data class LoadChartUi(val short: List<Float?>, val long: List<Float?>, val description: String)

@Immutable
data class FitnessState(
    val loading: Boolean = true,
    val chart: LoadChartUi? = null,
    /** Intensity Trend as a percent ("47%"). */
    val ratio: String? = null,
    /** Raw ratio for colouring. */
    val ratioValue: Double? = null,
    /** The status word (COROS shows the load comment). */
    val comment: String? = null,
    val status: TrainingStatusUi? = null,
    val running: RunningFitnessUi? = null,
    val vo2max: String? = null,
    val runningLevel: String? = null,
    val thresholdPace: String? = null,
    val predictions: List<MetricUi> = emptyList(),
    val footer: FooterUi? = null,
    val empty: EmptyUi? = null,
)

enum class HcStatus { Unknown, Unavailable, UpdateRequired, NotGranted, PartlyGranted, Granted }

enum class OnboardingChoice(val label: String, val useHealthConnect: Boolean, val useCoros: Boolean) {
    HealthConnect("Health Connect only", true, false),
    Coros("COROS account only", false, true),
    Both("Health Connect and COROS account", true, true),
}
