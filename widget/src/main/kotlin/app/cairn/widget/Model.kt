package app.cairn.widget

import app.cairn.core.model.Freshness

/** Whether COROS access works. Only matters when there is nothing to show. */
enum class AuthState { Ok, SignedOut, NeedsSignIn }

/** Which Cairn screen a tap opens (read by MainActivity from [EXTRA_SCREEN]). */
enum class Screen { Today, Activities, Account }

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
