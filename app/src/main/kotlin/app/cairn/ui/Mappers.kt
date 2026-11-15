package app.cairn.ui

import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.Derived
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.HrvDay
import app.cairn.core.model.SleepRating
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.WeeklyLoad
import app.cairn.widget.WidgetMapper
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
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

/** Everything the Today screen needs beyond the daily summary, sleep, recovery and load. */
data class TodayExtras(
    val vitals: List<VitalsDay> = emptyList(),
    val hrv: List<HrvDay> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val details: Map<String, ActivityDetail> = emptyMap(),
    val fitness: FitnessSnapshot? = null,
)

/** The order of the Today cards, as in the COROS app. */
enum class TodayCard { Header, Activity, HeartRate, RestingHr, Stress, Sleep, Hrv, WeeklyLoad, TrainingStatus, Recovery, RunningFitness }

object TodayOrder {
    val cards = listOf(
        TodayCard.Header, TodayCard.Activity, TodayCard.HeartRate, TodayCard.RestingHr, TodayCard.Stress, TodayCard.Sleep,
        TodayCard.Hrv, TodayCard.WeeklyLoad, TodayCard.TrainingStatus, TodayCard.Recovery, TodayCard.RunningFitness,
    )

    /** The cards [s] has data for, in COROS order. */
    fun present(s: TodayState): List<TodayCard> = cards.filter {
        when (it) {
            TodayCard.Header -> s.header.isNotEmpty()
            TodayCard.Activity -> s.todayActivity != null
            TodayCard.HeartRate -> s.heart != null
            TodayCard.RestingHr -> s.resting != null
            TodayCard.Stress -> s.stress != null
            TodayCard.Sleep -> s.sleep != null
            TodayCard.Hrv -> s.hrv != null
            TodayCard.WeeklyLoad -> s.weekly != null
            TodayCard.TrainingStatus -> s.status != null
            TodayCard.Recovery -> s.recovery != null
            TodayCard.RunningFitness -> s.running != null
        }
    }
}

/** Sport glyph, and the COROS-style title ("Sunday  Riverside Run"). */
object ActivityText {
    fun glyph(a: Activity): String {
        val t = a.sportType
        val n = a.sport.lowercase()
        return when {
            t != null && t in 100..103 || "run" in n -> "\uD83C\uDFC3"
            t == 104 || t == 105 || "hike" in n || "walk" in n -> "\uD83E\uDD7E"
            t != null && t in 200..299 || "bike" in n || "cycl" in n -> "\uD83D\uDEB4"
            t != null && t in 300..399 || "swim" in n -> "\uD83C\uDFCA"
            t != null && t in 400..499 || "strength" in n || "gym" in n -> "\uD83C\uDFCB"
            else -> Fmt.sport(a.sport).take(1).uppercase()
        }
    }

    /** "Outdoor Run" -> "Run". */
    fun shortSport(raw: String): String = Fmt.sport(raw).replace(Regex("^(Outdoor|Indoor)\\s+"), "").replaceFirstChar { it.titlecase() }

    /** Weekday, two spaces, then the place (the server's Location field) and the sport: "Sunday  Riverside Run". */
    fun title(a: Activity, locale: Locale): String {
        val place = a.location?.trim()?.takeIf { it.isNotEmpty() }
        val sport = shortSport(a.sport)
        // COROS's Location can already be the whole title ("Riverside Run"): never append the sport again.
        val name = when {
            place == null -> sport
            place.endsWith(sport, ignoreCase = true) -> place
            else -> "$place $sport"
        }
        return Fmt.weekday(a.date, locale) + "  " + name
    }
}

/** Month grouping for the Activities tab: "Nov  9.58 km  3 activities  2.2 hours". */
object Months {
    fun group(rows: List<Pair<Activity, ActivityRowUi>>, unit: DistanceUnit, locale: Locale): List<MonthUi> =
        rows.groupBy { YearMonth.from(it.first.date) }.toSortedMap(compareByDescending { it }).map { (ym, list) ->
            val meters = list.sumOf { it.first.distanceMeters ?: 0.0 }
            val sec = list.sumOf { it.first.durationSec ?: 0 }
            MonthUi(
                title = ym.month.getDisplayName(TextStyle.SHORT, locale),
                distance = Fmt.distance(meters, unit, locale),
                count = if (list.size == 1) "1 activity" else "${list.size} activities",
                hours = String.format(locale, "%.1f hours", sec / 3600.0),
                rows = list.map { it.second },
            )
        }
}

object AppMapper {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val WEEK_LETTERS = "MTWTFSS"

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
        extras: TodayExtras = TodayExtras(),
    ): TodayState {
        val stamps = mutableListOf<Instant>()
        val d = daily.firstOrNull { it.date == today }
        val night = sleeps.filter { it.asleepMinutes != null && !it.date.isBefore(today.minusDays(1)) }.maxByOrNull { it.date }
        night?.let { stamps += it.stamp.sourceUpdatedAt }
        val pct = recovery?.percent
        recovery?.takeIf { it.percent != null }?.let { stamps += it.stamp.sourceUpdatedAt }

        // Header row: steps, kcal, active minutes.
        val header = buildList {
            d?.steps?.let { stamps += it.sourceUpdatedAt; add(StatUi(Fmt.steps(it.value, locale), "steps")) }
            d?.calories?.let { stamps += it.sourceUpdatedAt; add(StatUi(Fmt.steps(it.value, locale), "kcal")) }
            d?.exerciseMinutes?.let { stamps += it.sourceUpdatedAt; add(StatUi(it.value.toString(), "min")) }
        }

        // Today's activity: the newest activity of today.
        val todays = extras.activities.filter { it.date == today }.maxWithOrNull(compareBy({ it.start ?: Instant.EPOCH }, { it.id }))
        val activityUi = todays?.let { a ->
            stamps += a.stamp.sourceUpdatedAt
            val dist = a.distanceMeters?.takeIf { it > 0 }?.let { Fmt.distance(it, prefs.unit, locale) }
            val dur = a.durationSec?.takeIf { it > 0 }?.let(Fmt::clock)
            val pace = a.avgPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
            val title = ActivityText.title(a, locale)
            val date = Fmt.dateLong(a.date, locale)
            TodayActivityUi(a.id, ActivityText.glyph(a), title, date, dist, dur, pace, listOfNotNull("Today's activity", title, date, dist, dur, pace).joinToString(". "))
        }

        // Heart rate (daily average with min and max), resting HR, stress: the dedicated tools.
        val vToday = extras.vitals.firstOrNull { it.date == today }
        val heart = vToday?.avgHr?.let { avg ->
            stamps += vToday.stamp.sourceUpdatedAt
            val range = if (vToday.minHr != null && vToday.maxHr != null) "${vToday.minHr}-${vToday.maxHr}" else null
            HeartUi(avg.toString(), "bpm", "Daily average", range, listOfNotNull("Heart rate, daily average $avg bpm", range?.let { "minimum ${vToday.minHr}, maximum ${vToday.maxHr}" }).joinToString(". "))
        }
        // The row of the latest date from queryRestingHeartRate; never the daily-summary figure (it can differ by 1 bpm).
        val restingRow = extras.vitals.filter { it.restingHr != null && !it.date.isAfter(today) }.maxByOrNull { it.date }
        val resting = restingRow?.let { row ->
            stamps += row.stamp.sourceUpdatedAt
            val rhr = row.restingHr!!
            val week = extras.vitals.filter { !it.date.isBefore(today.minusDays(6)) }.mapNotNull { it.restingHr }
            val avg = week.takeIf { it.isNotEmpty() }?.let { Math.round(it.average()).toInt() }
            val day = if (row.date == today) "Today" else Fmt.dateLong(row.date, locale)
            RestingUi(rhr.toString(), "bpm", day, avg?.let { "7-day avg $it bpm" }, listOfNotNull("Resting heart rate $rhr bpm", day, avg?.let { "7-day average $it bpm" }).joinToString(". "))
        }
        val stressValue = vToday?.avgStress ?: d?.avgStress?.value
        val stress = stressValue?.let { v ->
            (vToday?.takeIf { it.avgStress != null }?.stamp?.sourceUpdatedAt ?: d?.avgStress?.sourceUpdatedAt)?.let { stamps += it }
            StressUi(v.toString(), "Average Stress", "Average stress $v")
        }

        // Overnight HRV: the newest night of the last two wake-up days.
        val hrvDay = extras.hrv.filter { it.avgMs != null && !it.date.isBefore(today.minusDays(1)) }.maxByOrNull { it.date }
        val hrv = hrvDay?.let { h ->
            stamps += h.stamp.sourceUpdatedAt
            val range = if (h.normalLowMs != null && h.normalHighMs != null) "Normal Range ${h.normalLowMs}-${h.normalHighMs} ms" else null
            val base = h.baselineMs?.let { "Baseline $it ms" }
            val avg = "Average ${h.avgMs} ms"
            HrvUi(h.assessment, avg, range, base, listOfNotNull("Overnight HRV", h.assessment, avg, range, base).joinToString(". "))
        }

        // Weekly Training Load: sum of the activity loads of the last 7 days.
        val week = WeeklyLoad.compute(extras.activities, extras.details, today)
        val haveLoads = week.activityCount - week.missing > 0 || (week.activityCount == 0 && extras.details.isNotEmpty())
        val weekly = if (!haveLoads) null else {
            extras.details.values.maxOfOrNull { it.stamp.sourceUpdatedAt }?.let { stamps += it }
            val peak = (week.weekBars.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1)
            val bars = week.weekBars.mapIndexed { i, v -> WeekBarUi(WEEK_LETTERS[i].toString(), ((v ?: 0).toFloat() / peak).coerceIn(0f, 1f), today.dayOfWeek.value - 1 == i) }
            val note = if (week.missing > 0) "${week.missing} of ${week.activityCount} activities still loading" else null
            WeeklyLoadUi(week.total.toString(), null, bars, note, listOfNotNull("Weekly training load ${week.total}", note).joinToString(". "))
        }

        // Training Status: COROS shows the load comment as the status word.
        val latest = load.filter { it.shortTermLoad != null || it.longTermLoad != null || it.loadRatio != null || it.comment != null }.maxByOrNull { it.date }
        val statusUi = latest?.let { l ->
            stamps += l.stamp.sourceUpdatedAt
            statusUi(l, if (haveLoads) week.total else null, locale)
        }

        val level = recovery?.level?.takeIf { it.isNotBlank() }?.let(Fmt::sport) ?: pct?.let(WidgetMapper::levelFor)
        val full = pct != null && (pct >= 100 || recovery?.hoursToFullRecovery == 0)
        val time = if (full) "Fully recovered" else Fmt.timeToFull(recovery?.hoursToFullRecovery)
        val recoveryRing = pct?.let {
            RingUi((it / 100f).coerceIn(0f, 1f), Fmt.percent(it), level.orEmpty(), listOfNotNull("Recovery $it percent", level, time).joinToString(". "))
        }

        val running = extras.fitness?.takeIf { it.runningLevel != null && it.runningLevel!! > 0 }?.let { f ->
            stamps += f.stamp.sourceUpdatedAt
            val v = f.runningLevel!!
            val marathon = f.predictionMarathonSec?.takeIf { it > 0 }?.let { "Marathon " + Fmt.raceTime(it) }
            val text = Fmt.runningLevel(v, locale)
            RunningFitnessUi(text, (v / 100.0).coerceIn(0.0, 1.0).toFloat(), marathon, listOfNotNull("Running fitness $text out of 100", marathon).joinToString(". "))
        }

        val sparks = listOf(
            Spark.build("Steps", lastSevenDays(today), daily.mapNotNull { s -> s.steps?.let { s.date to it.value } }.toMap()) { Fmt.steps(it, locale) },
            Spark.build("Sleep", lastSevenDays(today), sleeps.mapNotNull { s -> s.asleepMinutes?.let { s.date to it } }.toMap()) { Fmt.duration(it) },
            Spark.build("Training load", lastSevenDays(today), load.mapNotNull { l -> l.shortTermLoad?.let { l.date to it } }.toMap()) { it.toString() },
        )
        if (stamps.isEmpty()) return TodayState(loading = false, sparks = sparks, empty = authEmpty(auth, "No data yet today"))

        return TodayState(
            loading = false,
            header = header, todayActivity = activityUi, heart = heart, resting = resting, stress = stress,
            sleep = night?.let { sleepUi(it, zone) }, hrv = hrv, weekly = weekly, status = statusUi,
            recovery = recoveryRing, recoveryLevel = level, timeToFull = time, running = running,
            sparks = sparks,
            footer = WidgetMapper.footer(stamps, now, prefs),
        )
    }

    /** Training Status: status word = the load comment; Load Impact = short-term load; Base Fitness = long-term load. */
    fun statusUi(l: TrainingLoadDay, sevenDay: Int?, locale: Locale): TrainingStatusUi {
        val word = l.comment?.takeIf { it.isNotBlank() }?.let(Fmt::sport)
        val trend = l.loadRatio?.let(Fmt::intensityTrend)
        return TrainingStatusUi(
            word, sevenDay?.toString(), l.shortTermLoad?.let { Fmt.steps(it, locale) }, l.longTermLoad?.let { Fmt.steps(it, locale) }, trend,
            listOfNotNull(
                "Training status", word, sevenDay?.let { "7-Day Training Load $it" }, l.shortTermLoad?.let { "Load Impact $it" },
                l.longTermLoad?.let { "Base Fitness $it" }, l.loadRatio?.let { "Intensity Trend ${Derived.intensityTrendPercent(it)} percent" },
            ).joinToString(". "),
        )
    }

    private fun sleepUi(n: SleepNight, zone: ZoneId): SleepUi {
        val dur = Fmt.duration(n.asleepMinutes ?: 0)
        val stages = listOf(
            Triple(StageKind.Deep, "Deep", n.deepMinutes), Triple(StageKind.Light, "Light", n.lightMinutes),
            Triple(StageKind.Rem, "REM", n.remMinutes), Triple(StageKind.Awake, "Awake", n.awakeMinutes),
        ).mapNotNull { (k, l, m) -> m?.takeIf { it > 0 }?.let { StageUi(k, l, it, Fmt.duration(it)) } }
        val window = if (n.start != null && n.end != null) "${clock.format(n.start!!.atZone(zone))}–${clock.format(n.end!!.atZone(zone))}" else null
        val rating = n.score?.takeIf { it > 0 }?.let(SleepRating::word)
        val score = n.score?.let { "score $it" }
        return SleepUi(
            dur, n.score?.toString(), rating, window, stages,
            listOfNotNull("Sleep $dur", score, rating, window?.let { "from $it" }, stages.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.label} ${it.text}" }).joinToString(". "),
        )
    }

    fun activities(items: List<Activity>, auth: AuthState, now: Instant, today: LocalDate, prefs: DisplayPrefs = DisplayPrefs(), locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault(), maxRows: Int = 100): ActivitiesState {
        if (items.isEmpty()) return ActivitiesState(loading = false, empty = authEmpty(auth, "No activities yet"))
        val shown = items.take(maxRows)
        val rows = shown.map { a ->
            val sport = Fmt.sport(a.sport)
            val date = Fmt.dateLong(a.date, locale)
            val dist = a.distanceMeters?.takeIf { it > 0 }?.let { Fmt.distance(it, prefs.unit, locale) }
            val dur = a.durationSec?.takeIf { it > 0 }?.let(Fmt::clock)
            val pace = a.avgPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
            val hr = a.avgHr?.takeIf { it > 0 }?.let(Fmt::bpm)
            val kcal = a.calories?.takeIf { it > 0 }?.let { Fmt.steps(it, locale) + " kcal" }
            val stats = listOfNotNull(dist, dur, pace, hr, kcal)
            val title = ActivityText.title(a, locale)
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
            ActivityRowUi(a.id, ActivityText.glyph(a), sport, date, title, dist, dur, pace, stats, details, (listOf(title, date) + stats).joinToString(". "))
        }
        val months = Months.group(shown.zip(rows), prefs.unit, locale)
        return ActivitiesState(loading = false, rows = rows, months = months, footer = WidgetMapper.footer(items.map { it.stamp.sourceUpdatedAt }, now, prefs))
    }

    fun fitness(load: List<TrainingLoadDay>, snapshot: FitnessSnapshot?, auth: AuthState, now: Instant, today: LocalDate, prefs: DisplayPrefs = DisplayPrefs(), locale: Locale = Locale.getDefault(), weeklyTotal: Int? = null): FitnessState {
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
        val levelValue = snapshot?.runningLevel?.takeIf { it > 0 }
        val level = levelValue?.let { Fmt.runningLevel(it, locale) }
        val thr = snapshot?.thresholdPaceSecPerKm?.takeIf { it > 0 }?.let { Fmt.pace(it, prefs.unit) }
        if (stamps.isEmpty() || (!hasLoad && vo2 == null && level == null && thr == null && preds.isEmpty())) {
            return FitnessState(loading = false, empty = authEmpty(auth, "No fitness data yet"))
        }
        val chart = if (hasLoad) LoadChartUi(short, long, "Training load, last 7 days. Load Impact " + describeSeries(short, locale) + ". Base Fitness " + describeSeries(long, locale) + ".") else null
        val marathon = snapshot?.predictionMarathonSec?.takeIf { it > 0 }?.let { "Marathon " + Fmt.raceTime(it) }
        val running = levelValue?.let { RunningFitnessUi(level!!, (it / 100.0).coerceIn(0.0, 1.0).toFloat(), marathon, listOfNotNull("Running fitness $level out of 100", marathon).joinToString(". ")) }
        return FitnessState(
            loading = false,
            chart = chart,
            ratio = latest?.loadRatio?.let(Fmt::intensityTrend),
            ratioValue = latest?.loadRatio,
            comment = latest?.comment?.takeIf { it.isNotBlank() }?.let(Fmt::sport),
            status = latest?.let { statusUi(it, weeklyTotal, locale) },
            running = running,
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
