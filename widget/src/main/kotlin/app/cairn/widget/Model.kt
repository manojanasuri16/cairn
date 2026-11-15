package app.cairn.widget

import app.cairn.core.model.Freshness

/** Whether COROS access works. Only matters when there is nothing to show. */
enum class AuthState { Ok, SignedOut, NeedsSignIn }

/** Which Cairn screen a tap opens (read by MainActivity from [EXTRA_SCREEN]). */
enum class Screen { Today, Activities, Fitness, Account, Freshness }

enum class MessageKind { Empty, SignedOut, NeedsSignIn }

/** Shared footer: data age with a freshness tier. Stale wording is explicit, never colour alone. */
data class FooterUi(val text: String, val tier: Freshness, val shortText: String = text, val ageMs: Long = 0L) {
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
    /** Intensity Trend as COROS shows it ("47%"), or an en dash when no ratio is cached. */
    val ratioText: String,
    val comment: String,
    val vo2Text: String?,
    /** "+1", "-2" or "steady"; null when there is no earlier value to compare with. */
    val vo2Trend: String?,
    /** Numeric ratio behind [ratioText], for colour and the dial. Null when none is cached. */
    val ratio: Double? = null,
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
    /** Numeric sleep minutes and score, for rings and lanes. */
    val minutes: Int = 0,
    val score: Int? = null,
)

data class RacePrediction(val label: String, val time: String)

data class FitnessUi(
    val vo2Text: String?,
    val thresholdPace: String?,
    val predictions: List<RacePrediction>,
)

/** Semantic colour of a pill or bar. The word always sits next to it, so colour is never the only signal. */
enum class Tone { Good, Warn, Bad, Cool, Neutral }

/** One night in the HRV bars (nightly average in ms; null when that night has no reading). */
data class HrvNight(val letter: String, val ms: Int?, val isToday: Boolean, val spoken: String)

data class HrvUi(
    /** "65" and "ms": drawn together as "65 ms" (the unit smaller). */
    val number: String,
    val unit: String,
    val avgMs: Int,
    /** The server's own assessment word ("Elevated", "Normal", "Low"), unchanged. */
    val assessment: String,
    val tone: Tone,
    /** "Normal range 46-58 ms". Null when the server gave no range. */
    val rangeText: String?,
    val lowMs: Int?,
    val highMs: Int?,
    /** "Baseline 52 ms". */
    val baselineText: String?,
    val baselineMs: Int?,
    /** Seven nights ending today, oldest first. */
    val nights: List<HrvNight>,
) {
    val hero: String get() = "$number $unit"
}

data class HeartRateUi(
    /** The big number: resting HR when COROS has one, otherwise the daily average. */
    val number: String,
    val unit: String,
    /** "Resting HR" or "Daily average". */
    val label: String,
    val restingBpm: Int?,
    val avgBpm: Int?,
    val minBpm: Int?,
    val maxBpm: Int?,
    /** "7-day avg 53 bpm". */
    val sevenDayText: String?,
    /** "Daily average 70 bpm". */
    val dailyText: String?,
    /** "44-154 bpm". */
    val minMaxText: String?,
    /** Resting HR of the days that have one, oldest first (the sparkline). */
    val series: List<Int>,
) {
    val hero: String get() = "$number $unit"
}

data class StressBar(val letter: String, val value: Int?, val fraction: Float, val tone: Tone, val isToday: Boolean, val spoken: String)

data class StressUi(
    val number: String,
    val value: Int,
    /** COROS-style band word (an assumption, see docs/data-sources.md). */
    val level: String,
    val tone: Tone,
    /** "7-day avg 28". */
    val sevenDayText: String?,
    val bars: List<StressBar>,
)

data class MileageBar(val letter: String, val meters: Double, val fraction: Float, val isToday: Boolean, val spoken: String)

data class MileageUi(
    /** "25.10" and "km" / "mi": drawn together as "25.10 km". */
    val number: String,
    val unit: String,
    val totalMeters: Double,
    val count: Int,
    /** "3 activities", "1 activity", "0 activities". */
    val countText: String,
    /** "+12% vs last week"; null when last week has no distance. */
    val comparison: String?,
    val changePercent: Int?,
    /** Monday..Sunday. */
    val bars: List<MileageBar>,
    /** Running total at the end of each day Monday..today, as 0..1 of this week's total (the trend line). */
    val cumulative: List<Float>,
) {
    val hero: String get() = "$number $unit"
}
