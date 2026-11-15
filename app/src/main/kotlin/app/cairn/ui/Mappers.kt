package app.cairn.ui

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.TrainingLoadDay
import app.cairn.sync.RunRecord
import app.cairn.sync.SyncOutcome
import app.cairn.source.healthconnect.HcAvailability
import app.cairn.widget.AuthState
import app.cairn.widget.Fmt
import app.cairn.widget.WidgetMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun authEmpty(auth: AuthState, title: String): EmptyUi = when (auth) {
    AuthState.NeedsSignIn -> EmptyUi("Sign in to COROS again", "COROS needs you to sign in before Cairn can refresh.", EmptyAction.OpenAccount)
    AuthState.SignedOut -> EmptyUi("No source connected", "Connect Health Connect or sign in to COROS to see data here.", EmptyAction.OpenAccount)
    AuthState.Ok -> EmptyUi(title, "Nothing synced yet. Pull down to refresh, or open COROS so it can sync your watch.")
}

/** Seven consecutive days ending [today], oldest first. */
fun lastSevenDays(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }

object Spark {
    /** Values per day (null = no data that day). Description is plain words for screen readers. */
    fun build(title: String, days: List<LocalDate>, byDay: Map<LocalDate, Number>, format: (Int) -> String): SparkUi {
        val values = days.map { byDay[it]?.toFloat() }
        val present = values.filterNotNull()
        if (present.isEmpty()) return SparkUi(title, values, null, "$title, last 7 days: no data")
        val latest = values.lastOrNull { it != null }!!.toInt()
        val lo = format(present.min().toInt())
        val hi = format(present.max().toInt())
        return SparkUi(title, values, format(latest), "$title, last 7 days: latest ${format(latest)}, from $lo to $hi, ${present.size} of 7 days with data")
    }
}

object AppMapper {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun today(
        daily: List<DailySummary>,
        sleeps: List<SleepNight>,
        recovery: Recovery?,
        load: List<TrainingLoadDay>,
        auth: AuthState,
        now: Instant,
        today: LocalDate,
        prefs: DisplayPrefs = DisplayPrefs(),
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TodayState {
        val stamps = mutableListOf<Instant>()
        val d = daily.firstOrNull { it.date == today }
        val steps = d?.steps?.also { stamps += it.sourceUpdatedAt }
        val night = sleeps.filter { it.asleepMinutes != null && !it.date.isBefore(today.minusDays(1)) }.maxByOrNull { it.date }
        night?.let { stamps += it.stamp.sourceUpdatedAt }
        val pct = recovery?.percent
        recovery?.takeIf { it.percent != null }?.let { stamps += it.stamp.sourceUpdatedAt }
        val metrics = buildList {
            d?.restingHr?.let { stamps += it.sourceUpdatedAt; add(MetricUi("Resting heart rate", Fmt.bpm(it.value))) }
            d?.avgStress?.let { stamps += it.sourceUpdatedAt; add(MetricUi("Stress", it.value.toString())) }
            d?.calories?.let { stamps += it.sourceUpdatedAt; add(MetricUi("Calories", Fmt.steps(it.value, locale) + " kcal")) }
        }
        val sparks = listOf(
            Spark.build("Steps", lastSevenDays(today), daily.mapNotNull { s -> s.steps?.let { s.date to it.value } }.toMap()) { Fmt.steps(it, locale) },
            Spark.build("Sleep", lastSevenDays(today), sleeps.mapNotNull { s -> s.asleepMinutes?.let { s.date to it } }.toMap()) { Fmt.duration(it) },
            Spark.build("Training load", lastSevenDays(today), load.mapNotNull { l -> l.shortTermLoad?.let { l.date to it } }.toMap()) { it.toString() },
        )
        if (stamps.isEmpty()) return TodayState(loading = false, sparks = sparks, empty = authEmpty(auth, "No data yet today"))

        val level = recovery?.level?.takeIf { it.isNotBlank() }?.let(Fmt::sport) ?: pct?.let(WidgetMapper::levelFor)
        val full = pct != null && (pct >= 100 || recovery?.hoursToFullRecovery == 0)
        val recoveryRing = pct?.let {
            val time = if (full) "Fully recovered" else Fmt.timeToFull(recovery?.hoursToFullRecovery)
            RingUi((it / 100f).coerceIn(0f, 1f), "$it%", level.orEmpty(), listOfNotNull("Recovery $it percent", level, time).joinToString(". "))
        }
        val stepsRing = steps?.let {
            RingUi(
                (it.value.toFloat() / WidgetMapper.STEP_GOAL).coerceIn(0f, 1f), Fmt.steps(it.value, locale), "steps",
                "Steps ${Fmt.steps(it.value, locale)} of ${Fmt.steps(WidgetMapper.STEP_GOAL, locale)} goal",
            )
        }
        return TodayState(
            loading = false,
            recovery = recoveryRing,
            recoveryLevel = level,
            timeToFull = if (full) "Fully recovered" else Fmt.timeToFull(recovery?.hoursToFullRecovery),
            steps = stepsRing,
            sleep = night?.let { sleepUi(it, zone) },
            metrics = metrics,
            sparks = sparks,
            footer = WidgetMapper.footer(stamps, now, prefs),
        )
    }

    private fun sleepUi(n: SleepNight, zone: ZoneId): SleepUi {
        val dur = Fmt.duration(n.asleepMinutes ?: 0)
        val stages = listOf(
            Triple(StageKind.Deep, "Deep", n.deepMinutes), Triple(StageKind.Light, "Light", n.lightMinutes),
            Triple(StageKind.Rem, "REM", n.remMinutes), Triple(StageKind.Awake, "Awake", n.awakeMinutes),
        ).mapNotNull { (k, l, m) -> m?.takeIf { it > 0 }?.let { StageUi(k, l, it, Fmt.duration(it)) } }
        val window = if (n.start != null && n.end != null) "${clock.format(n.start!!.atZone(zone))}–${clock.format(n.end!!.atZone(zone))}" else null
        val score = n.score?.let { "score $it" }
        return SleepUi(
            dur, n.score?.toString(), window, stages,
            listOfNotNull("Sleep $dur", score, window?.let { "from $it" }, stages.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.label} ${it.text}" }).joinToString(". "),
        )
    }

    fun activities(items: List<Activity>, auth: AuthState, now: Instant, today: LocalDate, prefs: DisplayPrefs = DisplayPrefs(), locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): ActivitiesState {
        if (items.isEmpty()) return ActivitiesState(loading = false, empty = authEmpty(auth, "No activities yet"))
        val rows = items.take(20).map { a ->
            val sport = Fmt.sport(a.sport)
            val date = Fmt.date(a.date, today, locale)
            val dist = a.distanceMeters?.takeIf { it > 0 }?.let { Fmt.distance(it, prefs.unit, locale) }
            val dur = a.durationSec?.takeIf { it > 0 }?.let(Fmt::durationSec)
            val pace = a.avgPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
            val hr = a.avgHr?.takeIf { it > 0 }?.let(Fmt::bpm)
            val kcal = a.calories?.takeIf { it > 0 }?.let { Fmt.steps(it, locale) + " kcal" }
            val stats = listOfNotNull(dist, dur, pace, hr, kcal)
            val details = buildList {
                add(MetricUi("Sport", sport))
                add(MetricUi("Date", a.start?.let { "$date, ${clock.format(it.atZone(zone))}" } ?: date))
                dist?.let { add(MetricUi("Distance", it)) }
                dur?.let { add(MetricUi("Duration", it)) }
                pace?.let { add(MetricUi("Average pace", it)) }
                hr?.let { add(MetricUi("Average heart rate", it)) }
                kcal?.let { add(MetricUi("Calories", it)) }
                a.location?.takeIf { it.isNotBlank() }?.let { add(MetricUi("Location", it)) }
                add(MetricUi("Source", if (a.stamp.source.name == "COROS_MCP") "COROS" else "Health Connect"))
            }
            ActivityRowUi(a.id, sport.take(1).uppercase(), sport, date, stats, details, (listOf(sport, date) + stats).joinToString(". "))
        }
        return ActivitiesState(loading = false, rows = rows, footer = WidgetMapper.footer(items.map { it.stamp.sourceUpdatedAt }, now, prefs))
    }

    fun fitness(load: List<TrainingLoadDay>, snapshot: FitnessSnapshot?, auth: AuthState, now: Instant, today: LocalDate, prefs: DisplayPrefs = DisplayPrefs(), locale: Locale = Locale.getDefault()): FitnessState {
        val days = lastSevenDays(today)
        val byDay = load.associateBy { it.date }
        val short = days.map { byDay[it]?.shortTermLoad?.toFloat() }
        val long = days.map { byDay[it]?.longTermLoad?.toFloat() }
        val latest = load.filter { it.shortTermLoad != null || it.longTermLoad != null || it.loadRatio != null }.maxByOrNull { it.date }
        val hasLoad = short.any { it != null } || long.any { it != null }
        val preds = listOf(
            "5K" to snapshot?.prediction5kSec, "10K" to snapshot?.prediction10kSec,
            "Half marathon" to snapshot?.predictionHalfSec, "Marathon" to snapshot?.predictionMarathonSec,
        ).mapNotNull { (l, s) -> s?.takeIf { it > 0 }?.let { MetricUi(l, Fmt.raceTime(it)) } }
        val stamps = listOfNotNull(latest?.stamp?.sourceUpdatedAt, snapshot?.stamp?.sourceUpdatedAt)
        val vo2 = snapshot?.vo2max?.takeIf { it > 0 }?.toString()
        val level = snapshot?.runningLevel?.takeIf { it > 0 }?.toString()
        val thr = snapshot?.thresholdPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
        if (stamps.isEmpty() || (!hasLoad && vo2 == null && level == null && thr == null && preds.isEmpty())) {
            return FitnessState(loading = false, empty = authEmpty(auth, "No fitness data yet"))
        }
        val chart = if (hasLoad) LoadChartUi(short, long, "Training load, last 7 days. Short-term " + describeSeries(short, locale) + ". Long-term " + describeSeries(long, locale) + ".") else null
        return FitnessState(
            loading = false,
            chart = chart,
            ratio = latest?.loadRatio?.let { String.format(locale, "%.2f", it) },
            comment = latest?.comment?.takeIf { it.isNotBlank() }?.let(Fmt::sport),
            vo2max = vo2, runningLevel = level, thresholdPace = thr, predictions = preds,
            footer = WidgetMapper.footer(stamps, now, prefs),
        )
    }

    private fun describeSeries(v: List<Float?>, locale: Locale): String {
        val p = v.filterNotNull()
        return if (p.isEmpty()) "no data" else "latest ${Fmt.steps(v.last { it != null }!!.toInt(), locale)}, peak ${Fmt.steps(p.max().toInt(), locale)}"
    }
}

object SourceStatus {
    /** Health Connect row in Settings. [granted] null = not read yet. */
    fun healthConnect(availability: HcAvailability?, granted: Set<String>?, needed: Set<String>): HcStatus = when {
        availability == null || granted == null && availability == HcAvailability.Available -> HcStatus.Unknown
        availability == HcAvailability.Unavailable -> HcStatus.Unavailable
        availability == HcAvailability.UpdateRequired -> HcStatus.UpdateRequired
        granted!!.containsAll(needed) -> HcStatus.Granted
        granted.any { it in needed } -> HcStatus.PartlyGranted
        else -> HcStatus.NotGranted
    }

    fun label(s: HcStatus): String = when (s) {
        HcStatus.Unknown -> "Checking Health Connect"
        HcStatus.Unavailable -> "Health Connect is not available on this phone"
        HcStatus.UpdateRequired -> "Health Connect needs an update"
        HcStatus.NotGranted -> "Permission not granted"
        HcStatus.PartlyGranted -> "Some permissions are missing"
        HcStatus.Granted -> "Permission granted"
    }

    fun canGrant(s: HcStatus) = s == HcStatus.NotGranted || s == HcStatus.PartlyGranted
}

/** Haptic outcome of a manual refresh. */
object RefreshFeedback {
    /** Positive only when the run that we started finished cleanly. */
    fun positive(run: RunRecord?, startedMs: Long): Boolean = run != null && run.atMs >= startedMs && run.outcome == SyncOutcome.SUCCESS.name
}
