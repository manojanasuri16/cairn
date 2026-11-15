package app.cairn.widget

import app.cairn.core.common.RelativeAge
import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.Derived
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Freshness
import app.cairn.core.model.HrvDay
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.WeeklyLoad
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.TrainingLoadDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Pure mapping from domain types plus "now" to widget UI models. */
object WidgetMapper {
    const val STEP_GOAL = 10_000

    fun footer(stamps: List<Instant>, now: Instant, prefs: DisplayPrefs = DisplayPrefs()): FooterUi {
        val newest = stamps.max()
        val age = Duration.between(newest, now).let { if (it.isNegative) Duration.ZERO else it }
        val tier = Freshness.fromAge(age, prefs.freshLimit, prefs.agingLimit)
        val rel = RelativeAge.format(age)
        val text = if (tier == Freshness.Stale) "Stale, last synced ${rel.removePrefix("Updated ")}" else rel
        val short = rel.removePrefix("Updated ").let { if (tier == Freshness.Stale) "Stale $it" else it }
        return FooterUi(text, tier, short, age.toMillis())
    }

    fun today(
        daily: DailySummary?,
        sleep: SleepNight?,
        recovery: Recovery?,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
        prefs: DisplayPrefs = DisplayPrefs(),
        vitals: VitalsDay? = null,
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
        // COROS shows queryRestingHeartRate's figure, which can differ by 1 bpm from the daily summary: prefer it.
        val v = vitals?.takeIf { it.date == today }
        val resting = v?.restingHr?.let { it to v.stamp.sourceUpdatedAt } ?: d?.restingHr?.let { it.value to it.sourceUpdatedAt }
        resting?.let { (bpm, at) -> stamps += at; metrics += Metric(MetricId.RestingHr, "Resting HR", Fmt.bpm(bpm)) }
        if (recovery?.percent != null) {
            stamps += recovery.stamp.sourceUpdatedAt
            metrics += Metric(MetricId.Recovery, "Recovery", "${recovery.percent}%")
        }
        d?.calories?.let { stamps += it.sourceUpdatedAt; metrics += Metric(MetricId.Calories, "Calories", Fmt.steps(it.value, locale) + " kcal") }
        val stress = v?.avgStress?.let { it to v.stamp.sourceUpdatedAt } ?: d?.avgStress?.let { it.value to it.sourceUpdatedAt }
        stress?.let { (n, at) -> stamps += at; metrics += Metric(MetricId.Stress, "Stress", n.toString()) }
        if (stamps.isEmpty()) return empty(auth, "No data yet today", Screen.Today)

        val body = TodayUi(
            stepsText = steps?.let { Fmt.steps(it.value, locale) },
            stepsFraction = steps?.let { (it.value.toFloat() / STEP_GOAL).coerceIn(0f, 1f) } ?: 0f,
            metrics = metrics,
        )
        val footer = footer(stamps, now, prefs)
        val parts = buildList {
            add("Today")
            add(body.stepsText?.let { "$it steps" } ?: "No steps yet today")
            metrics.forEach { add("${it.label} ${it.value}") }
            add(footer.text)
        }
        return WidgetUi.Content(body, footer, parts.joinToString(". "), Screen.Today)
    }

    fun recovery(recovery: Recovery?, auth: AuthState, now: Instant, prefs: DisplayPrefs = DisplayPrefs()): WidgetUi<RecoveryUi> {
        val pct = recovery?.percent ?: return empty(auth, "No recovery data", Screen.Today)
        val full = pct >= 100 || recovery.hoursToFullRecovery == 0
        val level = recovery.level?.takeIf { it.isNotBlank() }?.let(Fmt::sport) ?: levelFor(pct)
        val time = if (full) "Fully recovered" else Fmt.timeToFull(recovery.hoursToFullRecovery)
        val body = RecoveryUi("$pct%", (pct / 100f).coerceIn(0f, 1f), level, time)
        val footer = footer(listOf(recovery.stamp.sourceUpdatedAt), now, prefs)
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
        prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<ActivityUi> {
        if (activity == null) return empty(auth, "No activities yet", Screen.Activities)
        val body = ActivityUi(
            sport = Fmt.sport(activity.sport),
            date = Fmt.date(activity.date, today, locale),
            distance = activity.distanceMeters?.takeIf { it > 0 }?.let { Fmt.distance(it, prefs.unit, locale) },
            duration = activity.durationSec?.takeIf { it > 0 }?.let(Fmt::durationSec),
            pace = activity.avgPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) },
            avgHr = activity.avgHr?.takeIf { it > 0 }?.let(Fmt::bpm),
        )
        val footer = footer(listOf(activity.stamp.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull("Last activity", body.sport, body.date, body.distance, body.duration, body.pace, body.avgHr?.let { "average heart rate $it" }, footer.text)
            .joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Activities)
    }

    /** Days shown in the load bars, ending today. */
    const val LOAD_DAYS = 7

    fun trainingLoad(
        days: List<TrainingLoadDay>,
        fitness: FitnessSnapshot?,
        previousVo2max: Int?,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
        prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<TrainingLoadUi> {
        val past = days.filter { !it.date.isAfter(today) }
        val latest = past.maxByOrNull { it.date }
        val vo2 = fitness?.vo2max?.takeIf { it > 0 }
        val byDate = past.associateBy { it.date }
        val window = (LOAD_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val loads = window.map { d -> byDate[d]?.shortTermLoad?.takeIf { it >= 0 } }
        if (latest == null && vo2 == null) return empty(auth, "No training load yet", Screen.Fitness)
        val peak = (loads.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1)
        val bars = window.mapIndexed { i, d ->
            val v = loads[i]
            LoadBar(
                letter = d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                fraction = if (v == null) 0f else (v.toFloat() / peak).coerceIn(0f, 1f),
                isToday = d == today,
                spoken = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + " " + (v?.let { "load $it" } ?: "no data"),
            )
        }
        val ratio = latest?.loadRatio?.takeIf { it > 0 }
        val comment = latest?.comment?.takeIf { it.isNotBlank() }?.let(Fmt::sport) ?: ratio?.let(::ratioLabel) ?: "No trend yet"
        val trend = if (vo2 != null && previousVo2max != null && previousVo2max > 0) Fmt.trend(vo2 - previousVo2max) else null
        val body = TrainingLoadUi(bars, ratio?.let(Fmt::intensityTrend) ?: "\u2013", comment, vo2?.let { "VO2max $it" }, trend, ratio)
        val stamps = listOfNotNull(latest?.stamp?.sourceUpdatedAt, fitness?.takeIf { vo2 != null }?.stamp?.sourceUpdatedAt)
        val footer = footer(stamps, now, prefs)
        val desc = listOfNotNull(
            "Training load",
            ratio?.let { "Intensity Trend ${Derived.intensityTrendPercent(it)} percent" }, comment,
            body.vo2Text, trend?.let { "VO2max trend ${Fmt.trendSpoken(it)}" },
            "Last seven days: " + bars.joinToString(", ") { it.spoken },
            footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Fitness)
    }

    /** Fallback label when COROS gave no comment. Bands follow the usual acute:chronic guidance. */
    fun ratioLabel(r: Double): String = when {
        r < 0.8 -> "Low"
        r <= 1.3 -> "Optimal"
        r <= 1.5 -> "High"
        else -> "Very high"
    }

    fun sleep(
        night: SleepNight?,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<SleepUi> {
        val asleep = night?.asleepMinutes?.takeIf { it > 0 }
        if (night == null || asleep == null || night.date.isBefore(today.minusDays(1))) return empty(auth, "No sleep data", Screen.Today)
        val raw = listOf(SleepStage.Deep to night.deepMinutes, SleepStage.Light to night.lightMinutes, SleepStage.Rem to night.remMinutes, SleepStage.Awake to night.awakeMinutes)
        val haveStages = night.deepMinutes != null && night.lightMinutes != null && night.remMinutes != null
        val stages = if (haveStages) raw.mapNotNull { (s, m) -> m?.takeIf { it > 0 }?.let { StageSlice(s, it, "${s.label} ${Fmt.duration(it)}") } }.takeIf { it.isNotEmpty() } else null
        val note = if (stages != null && stagesApproximate(night, asleep)) "Stage times are approximate" else null
        val window = if (night.start != null && night.end != null) Fmt.window(night.start!!, night.end!!, zone) else null
        val body = SleepUi(Fmt.duration(asleep), night.score?.takeIf { it > 0 }?.let { "Score $it" }, window, stages, note, asleep, night.score?.takeIf { it > 0 })
        val footer = footer(listOf(night.stamp.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull(
            "Sleep", "Slept ${body.durationText}", body.scoreText, window?.let { "from ${Fmt.windowSpoken(night.start!!, night.end!!, zone)}" },
            stages?.joinToString(", ") { it.text }, note, footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Today)
    }

    /** Stage minutes are approximate when the source estimates them (Health Connect) or deep+light+REM misses the sleep time by more than 5% (min 10 min). */
    fun stagesApproximate(n: SleepNight, asleep: Int): Boolean {
        if (n.stamp.source == DataSource.HEALTH_CONNECT) return true
        val sum = (n.deepMinutes ?: 0) + (n.lightMinutes ?: 0) + (n.remMinutes ?: 0)
        return kotlin.math.abs(sum - asleep) > maxOf(10, asleep / 20)
    }

    fun fitness(snapshot: FitnessSnapshot?, auth: AuthState, now: Instant, prefs: DisplayPrefs = DisplayPrefs()): WidgetUi<FitnessUi> {
        val s = snapshot ?: return empty(auth, "No fitness data", Screen.Fitness)
        val vo2 = s.vo2max?.takeIf { it > 0 }?.toString()
        val threshold = s.thresholdPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
        val preds = listOf("5K" to s.prediction5kSec, "10K" to s.prediction10kSec, "HM" to s.predictionHalfSec, "M" to s.predictionMarathonSec)
            .mapNotNull { (l, t) -> t?.takeIf { it > 0 }?.let { RacePrediction(l, Fmt.raceTime(it)) } }
        if (vo2 == null && threshold == null && preds.isEmpty()) return empty(auth, "No fitness data", Screen.Fitness)
        val footer = footer(listOf(s.stamp.sourceUpdatedAt), now, prefs)
        val spokenPreds = preds.map { (if (it.label == "HM") "half marathon" else if (it.label == "M") "marathon" else it.label) + " " + it.time }
        val desc = listOfNotNull(
            "Fitness", vo2?.let { "VO2max $it" }, threshold?.let { "Threshold pace $it" },
            spokenPreds.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Predicted "), footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(FitnessUi(vo2, threshold, preds), footer, desc, Screen.Fitness)
    }

    /**
     * The Overview stats that need the newer data (heart rate, HRV, weekly load, today's run). Each item shows only when
     * its source has a value for today; [OverviewExtras.footer] is null when none do.
     */
    fun overviewExtras(
        vitals: VitalsDay?, hrv: HrvDay?, week: WeeklyLoad?, weekStamp: Instant?, todayRun: Activity?,
        now: Instant, today: LocalDate, prefs: DisplayPrefs = DisplayPrefs(), locale: Locale = Locale.getDefault(),
    ): OverviewExtras {
        val items = mutableListOf<OverviewItem>()
        val stamps = mutableListOf<Instant>()
        vitals?.takeIf { it.date == today && it.avgHr != null }?.let { items += OverviewItem(OverviewId.HeartRate, it.avgHr.toString(), "bpm"); stamps += it.stamp.sourceUpdatedAt }
        hrv?.takeIf { it.date == today && it.avgMs != null }?.let { items += OverviewItem(OverviewId.Hrv, it.avgMs.toString(), "ms"); stamps += it.stamp.sourceUpdatedAt }
        if (week != null && weekStamp != null && (week.activityCount - week.missing > 0 || week.activityCount == 0)) {
            val peak = (week.weekBars.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1)
            items += OverviewItem(OverviewId.WeeklyLoad, week.total.toString(), "", null, null, week.weekBars.map { ((it ?: 0).toFloat() / peak).coerceIn(0f, 1f) })
            stamps += weekStamp
        }
        todayRun?.takeIf { it.date == today && (it.distanceMeters ?: 0.0) > 0 }?.let {
            val (n, u) = OverviewMapper.splitUnit(Fmt.distance(it.distanceMeters!!, prefs.unit, locale))
            items += OverviewItem(OverviewId.TodayRun, n, u); stamps += it.stamp.sourceUpdatedAt
        }
        return OverviewExtras(items, stamps.takeIf { it.isNotEmpty() }?.let { footer(it, now, prefs) })
    }

    private fun empty(auth: AuthState, emptyTitle: String, screen: Screen): WidgetUi.Message = when (auth) {
        AuthState.NeedsSignIn -> WidgetUi.Message(MessageKind.NeedsSignIn, "Sign in again", "COROS needs you to sign in. Tap to open Cairn.", Screen.Account)
        AuthState.SignedOut -> WidgetUi.Message(MessageKind.SignedOut, "Not signed in", "Connect COROS or Health Connect. Tap to open Cairn.", Screen.Account)
        AuthState.Ok -> WidgetUi.Message(MessageKind.Empty, emptyTitle, "Nothing synced yet. Tap to open Cairn.", screen)
    }
}
