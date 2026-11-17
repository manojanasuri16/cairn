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
import app.cairn.core.model.StressBand
import app.cairn.core.model.WeeklyMileage
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
            pace = activity.avgPaceSecPerKm?.takeIf { it > 0 && (activity.distanceMeters ?: 0.0) > 0 }?.let { Fmt.pace(it, prefs.unit) },
            avgHr = activity.avgHr?.takeIf { it > 0 }?.let(Fmt::bpm),
            calories = activity.calories?.takeIf { it > 0 }?.let { Fmt.steps(it, locale) + " kcal" },
        )
        val footer = footer(listOf(activity.stamp.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull("Last activity", body.sport, body.date, body.distance, body.duration, body.pace, body.avgHr?.let { "average heart rate $it" }, body.calories, footer.text)
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
        mileage: WeeklyMileage? = null, mileageStamp: Instant? = null,
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
        if (mileage != null && mileageStamp != null && mileage.count > 0) {
            val (n, u) = OverviewMapper.splitUnit(Fmt.distance(mileage.totalMeters, prefs.unit, locale))
            val peak = mileage.dayMeters.max().coerceAtLeast(1.0)
            items += OverviewItem(OverviewId.WeeklyMileage, n, u, null, null, mileage.dayMeters.map { (it / peak).toFloat() })
            stamps += mileageStamp
        }
        return OverviewExtras(items, stamps.takeIf { it.isNotEmpty() }?.let { footer(it, now, prefs) })
    }

    // ---- HRV, heart rate, stress, weekly mileage ----

    /** Semantic tone of an HRV assessment word, from the server's own wording. Unknown words stay neutral. */
    fun hrvTone(word: String): Tone {
        val w = word.lowercase()
        return when {
            "poor" in w || "unbalanced" in w -> Tone.Bad
            "below" in w || "low" in w -> Tone.Warn
            "above" in w || "elevated" in w || "high" in w -> Tone.Cool
            "normal" in w || "balanced" in w || "good" in w || "optimal" in w -> Tone.Good
            else -> Tone.Neutral
        }
    }

    private fun letter(d: LocalDate, locale: Locale) = d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
    private fun dayName(d: LocalDate, locale: Locale) = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    /** Overnight HRV: the latest night (today or yesterday's wake-up day), COROS's own assessment word and normal range, and seven nights. */
    fun hrv(
        days: List<HrvDay>, auth: AuthState, now: Instant, today: LocalDate,
        locale: Locale = Locale.getDefault(), prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<HrvUi> {
        val past = days.filter { !it.date.isAfter(today) && (it.avgMs ?: 0) > 0 }
        val latest = past.maxByOrNull { it.date }
        if (latest == null || latest.date.isBefore(today.minusDays(1))) return empty(auth, "No HRV data", Screen.Today)
        val avg = latest.avgMs!!
        val low = latest.normalLowMs?.takeIf { it > 0 }
        val high = latest.normalHighMs?.takeIf { it > 0 && (low == null || it >= low) }
        val range = if (low != null && high != null) low to high else null
        val word = latest.assessment?.trim()?.takeIf { it.isNotEmpty() }
            ?: range?.let { (l, h) -> if (avg < l) "Low" else if (avg > h) "Elevated" else "Normal" } ?: "No assessment"
        val byDate = past.associateBy { it.date }
        val nights = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val ms = byDate[d]?.avgMs
            HrvNight(letter(d, locale), ms, d == today, dayName(d, locale) + " " + (ms?.let { "$it milliseconds" } ?: "no data"))
        }
        val baseline = latest.baselineMs?.takeIf { it > 0 }
        val body = HrvUi(
            number = avg.toString(), unit = "ms", avgMs = avg, assessment = word, tone = hrvTone(word),
            rangeText = range?.let { (l, h) -> "Normal range $l-$h ms" }, lowMs = range?.first, highMs = range?.second,
            baselineText = baseline?.let { "Baseline $it ms" }, baselineMs = baseline, nights = nights,
        )
        val footer = footer(listOf(latest.stamp.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull(
            "Overnight HRV $avg milliseconds", word, range?.let { (l, h) -> "Normal range $l to $h milliseconds" },
            baseline?.let { "Baseline $it milliseconds" }, "Last seven nights: " + nights.joinToString(", ") { it.spoken }, footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Today)
    }

    /**
     * Heart rate as COROS shows it: Resting HR is the main number (the latest queryRestingHeartRate row, today or yesterday), with its
     * 7-day average, today's daily average with min and max, and the resting series for the sparkline. Without a resting row the
     * daily average is the main number.
     */
    fun heartRate(
        days: List<VitalsDay>, auth: AuthState, now: Instant, today: LocalDate,
        locale: Locale = Locale.getDefault(), prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<HeartRateUi> {
        val window = days.filter { !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(6)) }.sortedBy { it.date }
        val restRows = window.filter { (it.restingHr ?: 0) > 0 }
        val resting = restRows.lastOrNull()?.takeIf { !it.date.isBefore(today.minusDays(1)) }
        val avgRow = window.lastOrNull { (it.avgHr ?: 0) > 0 }?.takeIf { !it.date.isBefore(today.minusDays(1)) }
        if (resting == null && avgRow == null) return empty(auth, "No heart rate data", Screen.Today)
        val restingBpm = resting?.restingHr
        val avgBpm = avgRow?.avgHr
        val mean = restRows.takeIf { it.size >= 2 }?.let { r -> Math.round(r.sumOf { it.restingHr!! } / r.size.toDouble()).toInt() }
        val min = avgRow?.minHr?.takeIf { it > 0 }
        val max = avgRow?.maxHr?.takeIf { it > 0 }
        val minMax = if (min != null && max != null) "$min-$max bpm" else null
        val heroIsResting = restingBpm != null
        val body = HeartRateUi(
            number = (restingBpm ?: avgBpm!!).toString(), unit = "bpm", label = if (heroIsResting) "Resting HR" else "Daily average",
            restingBpm = restingBpm, avgBpm = avgBpm, minBpm = min, maxBpm = max,
            sevenDayText = if (restingBpm != null) mean?.let { "7-day avg ${Fmt.bpm(it)}" } else null,
            dailyText = avgBpm?.let { "Daily average ${Fmt.bpm(it)}" }, minMaxText = minMax,
            series = restRows.map { it.restingHr!! },
        )
        val footer = footer(listOfNotNull(resting?.stamp?.sourceUpdatedAt, avgRow?.stamp?.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull(
            if (heroIsResting) "Resting heart rate ${Fmt.bpm(restingBpm!!)}" else "Daily average heart rate ${Fmt.bpm(avgBpm!!)}",
            if (heroIsResting) mean?.let { "Seven day average ${Fmt.bpm(it)}" } else null,
            if (heroIsResting) avgBpm?.let { "Daily average ${Fmt.bpm(it)}" } else null,
            if (min != null && max != null) "Lowest $min, highest $max beats per minute" else null, footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Today)
    }

    /** The semantic tone of a stress band. */
    fun stressTone(band: StressBand): Tone = when (band) {
        StressBand.Relaxed -> Tone.Cool; StressBand.Low -> Tone.Good; StressBand.Medium -> Tone.Warn; StressBand.High -> Tone.Bad
    }

    /** Stress: the day's average (queryStressLevel), its band word (an assumption, see docs) and seven days of bars. */
    fun stress(
        days: List<VitalsDay>, auth: AuthState, now: Instant, today: LocalDate,
        locale: Locale = Locale.getDefault(), prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<StressUi> {
        val window = days.filter { !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(6)) && (it.avgStress ?: 0) > 0 }.sortedBy { it.date }
        val latest = window.lastOrNull()?.takeIf { !it.date.isBefore(today.minusDays(1)) }
        val value = latest?.avgStress
        val band = value?.let { StressBand.of(it) }
        if (latest == null || value == null || band == null) return empty(auth, "No stress data", Screen.Today)
        val byDate = window.associateBy { it.date }
        val scale = maxOf(40, window.maxOf { it.avgStress!! })
        val bars = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val v = byDate[d]?.avgStress
            val b = v?.let { StressBand.of(it) }
            StressBar(
                letter(d, locale), v, if (v == null) 0f else (v.toFloat() / scale).coerceIn(0f, 1f), b?.let(::stressTone) ?: Tone.Neutral, d == today,
                dayName(d, locale) + " " + (v?.let { "stress $it, ${b?.word}" } ?: "no data"),
            )
        }
        val mean = window.takeIf { it.size >= 2 }?.let { w -> Math.round(w.sumOf { it.avgStress!! } / w.size.toDouble()).toInt() }
        val body = StressUi(value.toString(), value, band.word, stressTone(band), mean?.let { "7-day avg $it" }, bars)
        val footer = footer(listOf(latest.stamp.sourceUpdatedAt), now, prefs)
        val desc = listOfNotNull(
            "Stress $value", band.word, mean?.let { "Seven day average $it" }, "Last seven days: " + bars.joinToString(", ") { it.spoken }, footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Today)
    }

    /** Weekly mileage: the sum of every cached activity's distance from Monday to Sunday, the count, and the change against last week. */
    fun weeklyMileage(
        activities: List<Activity>, auth: AuthState, now: Instant, today: LocalDate,
        locale: Locale = Locale.getDefault(), prefs: DisplayPrefs = DisplayPrefs(),
    ): WidgetUi<MileageUi> {
        val w = WeeklyMileage.compute(activities, today)
        if (w.count == 0 && w.lastWeekCount == 0) return empty(auth, "No activities yet", Screen.Activities)
        val (number, unit) = OverviewMapper.splitUnit(Fmt.distance(w.totalMeters, prefs.unit, locale))
        val countText = if (w.count == 1) "1 activity" else "${w.count} activities"
        val change = w.changePercent
        val comparison = change?.let { if (it == 0) "Same as last week" else (if (it > 0) "+$it%" else "$it%") + " vs last week" }
        val peak = w.dayMeters.max().coerceAtLeast(1.0)
        val todayIdx = today.dayOfWeek.value - 1
        val bars = w.dayMeters.mapIndexed { i, m ->
            val d = w.monday.plusDays(i.toLong())
            MileageBar(letter(d, locale), m, (m / peak).toFloat().coerceIn(0f, 1f), i == todayIdx, dayName(d, locale) + " " + if (d.isAfter(today)) "not yet" else Fmt.distance(m, prefs.unit, locale))
        }
        var run = 0.0
        val cumulative = (0..todayIdx).map { i -> run += w.dayMeters[i]; if (w.totalMeters > 0) (run / w.totalMeters).toFloat() else 0f }
        val body = MileageUi(number, unit, w.totalMeters, w.count, countText, comparison, change, bars, cumulative)
        val lastMonday = w.monday.minusDays(7)
        val stamps = activities.filter { !it.date.isBefore(lastMonday) && !it.date.isAfter(today) }.map { it.stamp.sourceUpdatedAt }
        val footer = footer(stamps, now, prefs)
        val desc = listOfNotNull(
            "Weekly mileage ${Fmt.distance(w.totalMeters, prefs.unit, locale)} this week", countText, comparison,
            "This week: " + bars.joinToString(", ") { it.spoken }, footer.text,
        ).joinToString(". ")
        return WidgetUi.Content(body, footer, desc, Screen.Activities)
    }

    private fun empty(auth: AuthState, emptyTitle: String, screen: Screen): WidgetUi.Message = when (auth) {
        AuthState.NeedsSignIn -> WidgetUi.Message(MessageKind.NeedsSignIn, "Sign in again", "COROS needs you to sign in. Tap to open Cairn.", Screen.Account)
        AuthState.SignedOut -> WidgetUi.Message(MessageKind.SignedOut, "Not signed in", "Connect COROS or Health Connect. Tap to open Cairn.", Screen.Account)
        AuthState.Ok -> WidgetUi.Message(MessageKind.Empty, emptyTitle, "Nothing synced yet. Tap to open Cairn.", screen)
    }
}
