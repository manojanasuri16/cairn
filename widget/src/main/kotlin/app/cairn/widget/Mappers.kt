package app.cairn.widget

import app.cairn.core.common.RelativeAge
import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.Freshness
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Pure mapping from domain types plus "now" to widget UI models. */
object WidgetMapper {
    const val STEP_GOAL = 10_000

    fun footer(stamps: List<Instant>, now: Instant): FooterUi {
        val newest = stamps.max()
        val age = Duration.between(newest, now).let { if (it.isNegative) Duration.ZERO else it }
        val tier = Freshness.fromAge(age)
        val rel = RelativeAge.format(age)
        val text = if (tier == Freshness.Stale) "Stale, last synced ${rel.removePrefix("Updated ")}" else rel
        return FooterUi(text, tier)
    }

    fun today(
        daily: DailySummary?,
        sleep: SleepNight?,
        recovery: Recovery?,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): WidgetUi<TodayUi> {
        val stamps = mutableListOf<Instant>()
        // Day rollover: a summary from a previous day is not "today".
        val d = daily?.takeIf { it.date == today }
        val steps = d?.steps?.also { stamps += it.sourceUpdatedAt }
        val metrics = mutableListOf<Metric>()
        if (sleep != null && sleep.asleepMinutes != null && !sleep.date.isBefore(today.minusDays(1))) {
            stamps += sleep.stamp.sourceUpdatedAt
            val score = sleep.score?.let { " · $it" } ?: ""
            metrics += Metric(MetricId.Sleep, "Sleep", Fmt.duration(sleep.asleepMinutes!!) + score)
        }
        d?.restingHr?.let { stamps += it.sourceUpdatedAt; metrics += Metric(MetricId.RestingHr, "Resting HR", Fmt.bpm(it.value)) }
        if (recovery?.percent != null) {
            stamps += recovery.stamp.sourceUpdatedAt
            metrics += Metric(MetricId.Recovery, "Recovery", "${recovery.percent}%")
        }
        d?.calories?.let { stamps += it.sourceUpdatedAt; metrics += Metric(MetricId.Calories, "Calories", Fmt.steps(it.value, locale) + " kcal") }
        d?.avgStress?.let { stamps += it.sourceUpdatedAt; metrics += Metric(MetricId.Stress, "Stress", it.value.toString()) }
        if (stamps.isEmpty()) return empty(auth, "No data yet today", Screen.Today)

        val body = TodayUi(
            stepsText = steps?.let { Fmt.steps(it.value, locale) },
            stepsFraction = steps?.let { (it.value.toFloat() / STEP_GOAL).coerceIn(0f, 1f) } ?: 0f,
            metrics = metrics,
        )
        val footer = footer(stamps, now)
        val parts = buildList {
            add("Today")
            add(body.stepsText?.let { "$it steps" } ?: "No steps yet today")
            metrics.forEach { add("${it.label} ${it.value}") }
            add(footer.text)
        }
        return WidgetUi.Content(body, footer, parts.joinToString(". "), Screen.Today)
    }

    fun recovery(recovery: Recovery?, auth: AuthState, now: Instant): WidgetUi<RecoveryUi> {
        val pct = recovery?.percent ?: return empty(auth, "No recovery data", Screen.Today)
        val full = pct >= 100 || recovery.hoursToFullRecovery == 0
        val level = recovery.level?.takeIf { it.isNotBlank() }?.let(Fmt::sport) ?: levelFor(pct)
        val time = if (full) "Fully recovered" else Fmt.timeToFull(recovery.hoursToFullRecovery)
        val body = RecoveryUi("$pct%", (pct / 100f).coerceIn(0f, 1f), level, time)
        val footer = footer(listOf(recovery.stamp.sourceUpdatedAt), now)
        val desc = listOfNotNull("Recovery $pct percent", level, time, footer.text).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Today)
    }

    fun levelFor(pct: Int): String = when {
        pct >= 80 -> "Well recovered"
        pct >= 50 -> "Recovering"
        else -> "Low recovery"
    }

    fun lastActivity(
        activity: Activity?,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): WidgetUi<ActivityUi> {
        if (activity == null) return empty(auth, "No activities yet", Screen.Activities)
        val body = ActivityUi(
            sport = Fmt.sport(activity.sport),
            date = Fmt.date(activity.date, today, locale),
            distance = activity.distanceMeters?.takeIf { it > 0 }?.let { Fmt.distanceKm(it, locale) },
            duration = activity.durationSec?.takeIf { it > 0 }?.let(Fmt::durationSec),
            pace = activity.avgPaceSecPerKm?.takeIf { it > 0 }?.let(Fmt::pace),
            avgHr = activity.avgHr?.takeIf { it > 0 }?.let(Fmt::bpm),
        )
        val footer = footer(listOf(activity.stamp.sourceUpdatedAt), now)
        val desc = listOfNotNull("Last activity", body.sport, body.date, body.distance, body.duration, body.pace, body.avgHr?.let { "average heart rate $it" }, footer.text)
            .joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Activities)
    }

    private fun empty(auth: AuthState, emptyTitle: String, screen: Screen): WidgetUi.Message = when (auth) {
        AuthState.NeedsSignIn -> WidgetUi.Message(MessageKind.NeedsSignIn, "Sign in again", "COROS needs you to sign in. Tap to open Cairn.", Screen.Account)
        AuthState.SignedOut -> WidgetUi.Message(MessageKind.SignedOut, "Not signed in", "Connect COROS or Health Connect. Tap to open Cairn.", Screen.Account)
        AuthState.Ok -> WidgetUi.Message(MessageKind.Empty, emptyTitle, "Nothing synced yet. Tap to open Cairn.", screen)
    }
}
