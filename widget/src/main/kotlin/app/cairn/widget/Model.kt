package app.cairn.widget

import app.cairn.core.model.Freshness

/** Whether COROS access works. Only matters when there is nothing to show. */
enum class AuthState { Ok, SignedOut, NeedsSignIn }

/** Which Cairn screen a tap opens (read by MainActivity from [EXTRA_SCREEN]). */
enum class Screen { Today, Activities, Fitness, Account, Freshness }

enum class MessageKind { Empty, SignedOut, NeedsSignIn }

/** Shared footer: data age with a freshness tier. Stale wording is explicit, never colour alone. */
data class FooterUi(val text: String, val tier: Freshness) {
    val opensCoros: Boolean get() = tier == Freshness.Stale
}

/** What a widget renders. Immutable, computed by [WidgetMapper] from domain types. */
sealed interface WidgetUi<out T> {
    data object Loading : WidgetUi<Nothing>
    data class Message(val kind: MessageKind, val title: String, val hint: String, val screen: Screen) : WidgetUi<Nothing>
    data class Content<T>(val body: T, val footer: FooterUi, val description: String, val screen: Screen) : WidgetUi<T>
}

enum class MetricId { Sleep, RestingHr, Recovery, Calories, Stress }

data class Metric(val id: MetricId, val label: String, val value: String)

data class TodayUi(
    /** Null when there are no steps for today yet. */
    val stepsText: String?,
    /** 0..1 progress towards the goal. */
    val stepsFraction: Float,
    /** Only metrics that exist, in priority order. */
    val metrics: List<Metric>,
)

data class RecoveryUi(
    val percentText: String,
    val fraction: Float,
    val level: String,
    val timeToFull: String?,
)

data class ActivityUi(
    val sport: String,
    val date: String,
    val distance: String?,
    val duration: String?,
    val pace: String?,
    val avgHr: String?,
)

/** One day in the 7-day load bars. [fraction] is relative to the busiest day shown. */
data class LoadBar(val letter: String, val fraction: Float, val isToday: Boolean, val spoken: String)

data class TrainingLoadUi(
    val bars: List<LoadBar>,
    /** "1.2", or an en dash when no ratio is cached. */
    val ratioText: String,
    val comment: String,
    val vo2Text: String?,
    /** "+1", "-2" or "steady"; null when there is no earlier value to compare with. */
    val vo2Trend: String?,
)

enum class SleepStage(val label: String) { Deep("Deep"), Light("Light"), Rem("REM"), Awake("Awake") }

data class StageSlice(val stage: SleepStage, val minutes: Int, val text: String)

data class SleepUi(
    val durationText: String,
    val scoreText: String?,
    /** "00:28\u201308:30". */
    val window: String?,
    /** Null when COROS gave no stage breakdown. */
    val stages: List<StageSlice>?,
    /** Set when stage minutes do not add up to the sleep time or came from a source that estimates them. */
    val stageNote: String?,
)

data class RacePrediction(val label: String, val time: String)

data class FitnessUi(
    val vo2Text: String?,
    val thresholdPace: String?,
    val predictions: List<RacePrediction>,
)
