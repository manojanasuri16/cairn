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

@Immutable
data class TodayState(
    val loading: Boolean = true,
    val recovery: RingUi? = null,
    val recoveryLevel: String? = null,
    val timeToFull: String? = null,
    val steps: RingUi? = null,
    val sleep: SleepUi? = null,
    val metrics: List<MetricUi> = emptyList(),
    val sparks: List<SparkUi> = emptyList(),
    val footer: FooterUi? = null,
    val empty: EmptyUi? = null,
)

@Immutable
data class ActivityRowUi(
    val id: String,
    val glyph: String,
    val sport: String,
    val date: String,
    /** Distance, duration, pace, heart rate, calories: only those that exist, in that order. */
    val stats: List<String>,
    val details: List<MetricUi>,
    val description: String,
)

@Immutable
data class ActivitiesState(
    val loading: Boolean = true,
    val rows: List<ActivityRowUi> = emptyList(),
    val footer: FooterUi? = null,
    val empty: EmptyUi? = null,
)

@Immutable
data class LoadChartUi(val short: List<Float?>, val long: List<Float?>, val description: String)

@Immutable
data class FitnessState(
    val loading: Boolean = true,
    val chart: LoadChartUi? = null,
    val ratio: String? = null,
    val comment: String? = null,
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
