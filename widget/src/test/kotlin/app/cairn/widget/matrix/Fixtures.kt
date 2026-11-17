package app.cairn.widget.matrix

import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.Freshness
import app.cairn.core.model.StressBand
import app.cairn.sync.RunRecord
import app.cairn.sync.SyncState
import app.cairn.widget.ActivityUi
import app.cairn.widget.AuthState
import app.cairn.widget.FitnessUi
import app.cairn.widget.FooterUi
import app.cairn.widget.Fmt
import app.cairn.widget.HeartRateUi
import app.cairn.widget.HrvNight
import app.cairn.widget.HrvUi
import app.cairn.widget.LoadBar
import app.cairn.widget.Metric
import app.cairn.widget.MetricId
import app.cairn.widget.MileageBar
import app.cairn.widget.MileageUi
import app.cairn.widget.OverviewId
import app.cairn.widget.OverviewItem
import app.cairn.widget.OverviewMapper
import app.cairn.widget.OverviewUi
import app.cairn.widget.RacePrediction
import app.cairn.widget.RecoveryUi
import app.cairn.widget.Screen
import app.cairn.widget.SleepStage
import app.cairn.widget.SleepUi
import app.cairn.widget.StageSlice
import app.cairn.widget.StressBar
import app.cairn.widget.StressUi
import app.cairn.widget.Tone
import app.cairn.widget.TodayUi
import app.cairn.widget.TrainingLoadUi
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetMapper
import app.cairn.widget.WidgetUi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** What the invariants compare the rendered tree against. Built from the same Ui the widget was given. */
data class Expected(
    val isContent: Boolean,
    /** The headline string (steps, percent, distance, duration, ratio, ...), or null when the state has none. */
    val hero: String?,
    /** Every value string (not labels) the widget may draw. */
    val values: List<String>,
    /** Today metric values / activity values / race times, for the planned-vs-rendered rule. */
    val metricValues: List<String> = emptyList(),
    val tileValues: List<String> = emptyList(),
    val raceTimes: List<String> = emptyList(),
    /** Day letters oldest first, for the RTL rule. */
    val letters: List<String> = emptyList(),
    val tier: Freshness? = null,
    val description: String? = null,
)

/** Synthetic fixtures (no real health data), built through [WidgetMapper] wherever the mapper takes plain inputs. */
object Fixtures {
    val NOW: Instant = Instant.parse("2026-11-17T09:00:00Z")
    val TODAY: LocalDate = LocalDate.of(2026, 11, 17)

    private fun footerFor(state: DataState): FooterUi {
        val age = when (state) {
            DataState.Aging -> Duration.ofHours(5)
            DataState.Stale -> Duration.ofHours(30)
            else -> Duration.ofMinutes(10)
        }
        return WidgetMapper.footer(listOf(NOW.minus(age)), NOW)
    }

    private fun auth(state: DataState) = when (state) {
        DataState.SignedOut -> AuthState.SignedOut
        DataState.NeedsSignIn -> AuthState.NeedsSignIn
        else -> AuthState.Ok
    }

    private val noData = setOf(DataState.NoData, DataState.SignedOut, DataState.NeedsSignIn)

    fun sync(state: DataState): SyncState? = when (state) {
        // FooterView derives the indicator from Instant.now(), so the sync clock is relative to the real clock, not [NOW].
        DataState.Syncing -> SyncState(userSyncRequestedMs = System.currentTimeMillis() - 30_000)
        DataState.SyncError -> SyncState(runs = listOf(RunRecord(System.currentTimeMillis() - 60_000, "USER", "FAILED")))
        else -> null
    }

    private fun letters(loc: Locale) = (6 downTo 0).map { TODAY.minusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, loc) }

    fun ui(kind: WidgetKind, state: DataState, locale: Locale, unit: DistanceUnit): WidgetUi<*> {
        if (state == DataState.Loading) return WidgetUi.Loading
        if (state in noData) return empty(kind, auth(state))
        val f = footerFor(state)
        return when (kind) {
            WidgetKind.Today -> today(state, locale, f)
            WidgetKind.Recovery -> recovery(state, f)
            WidgetKind.LastActivity -> activity(state, locale, unit, f)
            WidgetKind.TrainingLoad -> load(state, locale, f)
            WidgetKind.Sleep -> sleep(state, f)
            WidgetKind.Fitness -> fitness(state, unit, f)
            WidgetKind.Overview -> overview(state, locale, unit, f)
            WidgetKind.Hrv -> hrv(state, locale, f)
            WidgetKind.HeartRate -> heart(state, f)
            WidgetKind.Stress -> stress(state, locale, f)
            WidgetKind.WeeklyMileage -> mileage(state, locale, unit, f)
        }
    }

    private fun empty(kind: WidgetKind, a: AuthState): WidgetUi<*> = when (kind) {
        WidgetKind.Today -> WidgetMapper.today(null, null, null, a, NOW, TODAY)
        WidgetKind.Recovery -> WidgetMapper.recovery(null, a, NOW)
        WidgetKind.LastActivity -> WidgetMapper.lastActivity(null, a, NOW, TODAY)
        WidgetKind.TrainingLoad -> WidgetMapper.trainingLoad(emptyList(), null, null, a, NOW, TODAY)
        WidgetKind.Sleep -> WidgetMapper.sleep(null, a, NOW, TODAY)
        WidgetKind.Fitness -> WidgetMapper.fitness(null, a, NOW)
        WidgetKind.Overview -> OverviewMapper.build(
            WidgetMapper.today(null, null, null, a, NOW, TODAY), WidgetMapper.sleep(null, a, NOW, TODAY), WidgetMapper.recovery(null, a, NOW),
            WidgetMapper.trainingLoad(emptyList(), null, null, a, NOW, TODAY), WidgetMapper.fitness(null, a, NOW), WidgetMapper.lastActivity(null, a, NOW, TODAY),
        )
        WidgetKind.Hrv -> WidgetMapper.hrv(emptyList(), a, NOW, TODAY)
        WidgetKind.HeartRate -> WidgetMapper.heartRate(emptyList(), a, NOW, TODAY)
        WidgetKind.Stress -> WidgetMapper.stress(emptyList(), a, NOW, TODAY)
        WidgetKind.WeeklyMileage -> WidgetMapper.weeklyMileage(emptyList(), a, NOW, TODAY)
    }

    private fun <T> content(body: T, f: FooterUi, hero: String, screen: Screen = Screen.Today) =
        WidgetUi.Content(body, f, "$hero. ${f.text}", screen)

    // ---- Today ----
    private fun today(state: DataState, loc: Locale, f: FooterUi): WidgetUi.Content<TodayUi> {
        val ui = when (state) {
            DataState.Huge -> TodayUi(Fmt.steps(123_456, loc), 1f, listOf(
                Metric(MetricId.Sleep, "Sleep", Fmt.duration(26 * 60 + 3) + " · 100"), Metric(MetricId.RestingHr, "Resting HR", Fmt.bpm(212)),
                Metric(MetricId.Recovery, "Recovery", "100%"), Metric(MetricId.Calories, "Calories", Fmt.steps(12_345, loc) + " kcal"), Metric(MetricId.Stress, "Stress", "100")))
            DataState.Zero -> TodayUi("0", 0f, listOf(
                Metric(MetricId.Sleep, "Sleep", Fmt.duration(0)), Metric(MetricId.RestingHr, "Resting HR", Fmt.bpm(0)),
                Metric(MetricId.Recovery, "Recovery", "0%"), Metric(MetricId.Calories, "Calories", "0 kcal"), Metric(MetricId.Stress, "Stress", "0")))
            DataState.Partial -> TodayUi(null, 0f, listOf(Metric(MetricId.Recovery, "Recovery", "82%")))
            DataState.HcOnly -> TodayUi(Fmt.steps(8432, loc), 0.84f, listOf(Metric(MetricId.Sleep, "Sleep", Fmt.duration(461)), Metric(MetricId.Calories, "Calories", Fmt.steps(1820, loc) + " kcal")))
            else -> TodayUi(Fmt.steps(8432, loc), 0.84f, listOf(
                Metric(MetricId.Sleep, "Sleep", Fmt.duration(461) + " · 82"), Metric(MetricId.RestingHr, "Resting HR", Fmt.bpm(52)),
                Metric(MetricId.Recovery, "Recovery", "82%"), Metric(MetricId.Calories, "Calories", Fmt.steps(1820, loc) + " kcal"), Metric(MetricId.Stress, "Stress", "30")))
        }
        return content(ui, f, (ui.stepsText ?: "No steps yet today") + ". " + ui.metrics.joinToString(". ") { "${it.label} ${it.value}" })
    }

    // ---- Recovery ----
    private fun recovery(state: DataState, f: FooterUi): WidgetUi.Content<RecoveryUi> {
        val ui = when (state) {
            DataState.Huge -> RecoveryUi("100%", 1f, "Überdurchschnittlich erholt", "Fully recovered")
            DataState.Zero -> RecoveryUi("0%", 0f, "Low recovery", Fmt.timeToFull(48))
            DataState.Partial -> RecoveryUi("82%", 0.82f, "Well recovered", null)
            DataState.LongText -> RecoveryUi("82%", 0.82f, "Überdurchschnittlich erholt", "4 hrs till full recovery")
            else -> RecoveryUi("82%", 0.82f, "Well recovered", "4 hrs till full recovery")
        }
        return content(ui, f, "Recovery ${ui.percentText}. ${ui.level}")
    }

    // ---- Last activity ----
    private fun activity(state: DataState, loc: Locale, unit: DistanceUnit, f: FooterUi): WidgetUi.Content<ActivityUi> {
        val ui = when (state) {
            DataState.Huge -> ActivityUi("Ultra running", "Sunday, 15 Nov", Fmt.distance(168_420.0, unit, loc), Fmt.duration(26 * 60 + 3), Fmt.pace(212, unit), Fmt.bpm(212), Fmt.steps(12_345, loc) + " kcal")
            DataState.Zero -> ActivityUi("Walking", "Today", null, Fmt.duration(0), null, null, "0 kcal")
            DataState.Partial -> ActivityUi("Running", "Today", Fmt.distance(10_200.0, unit, loc), null, null, null)
            DataState.HcOnly -> ActivityUi("Running", "Today", Fmt.distance(10_200.0, unit, loc), Fmt.durationSec(3120), null, null)
            DataState.LongText -> ActivityUi("Trail running ultra", Fmt.dateLong(TODAY, loc), Fmt.distance(10_200.0, unit, loc), Fmt.durationSec(3120), Fmt.pace(307, unit), Fmt.bpm(152), "540 kcal")
            else -> ActivityUi("Running", "Today", Fmt.distance(10_200.0, unit, loc), Fmt.durationSec(3120), Fmt.pace(307, unit), Fmt.bpm(152), "540 kcal")
        }
        return content(ui, f, "Last activity. ${ui.sport}. ${ui.date}. ${ui.distance ?: ui.duration ?: ""}", Screen.Activities)
    }

    // ---- Training load ----
    private fun load(state: DataState, loc: Locale, f: FooterUi): WidgetUi.Content<TrainingLoadUi> {
        val fr = when (state) {
            DataState.Zero -> List(7) { 0f }
            DataState.Huge -> listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f)
            else -> listOf(0.4f, 0.65f, 0f, 0.8f, 0.55f, 1f, 0.7f)
        }
        val bars = fr.mapIndexed { i, v -> LoadBar(letters(loc)[i], v, i == 6, "day ${i + 1}") }
        val ui = when (state) {
            DataState.Huge -> TrainingLoadUi(bars, "999%", "Very high", "VO2max 99", "+12", 9.99)
            DataState.Zero -> TrainingLoadUi(bars, "0%", "Low", "VO2max 0", "steady", 0.0)
            DataState.Partial -> TrainingLoadUi(bars, "–", "No trend yet", null, null, null)
            DataState.LongText -> TrainingLoadUi(bars, "112%", "Überdurchschnittlich optimal", "VO2max 52", "+1", 1.12)
            else -> TrainingLoadUi(bars, "112%", "Optimal", "VO2max 52", "+1", 1.12)
        }
        return content(ui, f, "Training load. Intensity Trend ${ui.ratioText}", Screen.Fitness)
    }

    // ---- Sleep ----
    private fun sleep(state: DataState, f: FooterUi): WidgetUi.Content<SleepUi> {
        fun st(d: Int, l: Int, r: Int, a: Int) = listOf(
            StageSlice(SleepStage.Deep, d, "Deep " + Fmt.duration(d)), StageSlice(SleepStage.Light, l, "Light " + Fmt.duration(l)),
            StageSlice(SleepStage.Rem, r, "REM " + Fmt.duration(r)), StageSlice(SleepStage.Awake, a, "Awake " + Fmt.duration(a)))
        val ui = when (state) {
            DataState.Huge -> SleepUi(Fmt.duration(26 * 60 + 3), "Score 100", "00:28–08:30", st(600, 600, 300, 120), "Stage times are approximate", 1563, 100)
            DataState.Zero -> SleepUi(Fmt.duration(0), "Score 0", null, null, null, 0, 0)
            DataState.Partial -> SleepUi(Fmt.duration(461), null, null, null, null, 461, null)
            DataState.HcOnly -> SleepUi(Fmt.duration(461), null, "23:10–07:02", st(90, 250, 100, 21), "Stage times are approximate", 461, null)
            else -> SleepUi(Fmt.duration(461), "Score 84", "23:10–07:02", st(90, 250, 100, 21), null, 461, 84)
        }
        return content(ui, f, "Sleep. Slept ${ui.durationText}" + (ui.scoreText?.let { ". $it" } ?: ""))
    }

    // ---- Fitness ----
    private fun fitness(state: DataState, unit: DistanceUnit, f: FooterUi): WidgetUi.Content<FitnessUi> {
        val ui = when (state) {
            DataState.Huge -> FitnessUi("99", Fmt.pace(212, unit), listOf(RacePrediction("5K", Fmt.raceTime(21 * 3600 + 59 * 60 + 1)), RacePrediction("10K", Fmt.raceTime(45 * 3600)), RacePrediction("HM", Fmt.raceTime(99 * 3600)), RacePrediction("M", Fmt.raceTime(23 * 3600 + 30 * 60 + 12))))
            DataState.Zero -> FitnessUi("0", Fmt.pace(60, unit), listOf(RacePrediction("5K", "0:01")))
            DataState.Partial -> FitnessUi("52", null, emptyList())
            else -> FitnessUi("52", Fmt.pace(261, unit), listOf(RacePrediction("5K", "21:40"), RacePrediction("10K", "45:10"), RacePrediction("HM", "1:39:30"), RacePrediction("M", "3:30:12")))
        }
        return content(ui, f, "Fitness. VO2max ${ui.vo2Text ?: ""}", Screen.Fitness)
    }

    // ---- Overview (through OverviewMapper) ----
    private fun overview(state: DataState, loc: Locale, unit: DistanceUnit, f: FooterUi): WidgetUi<OverviewUi> {
        val t = today(state, loc, f); val r = recovery(state, f); val l = load(state, loc, f); val s = sleep(state, f); val fi = fitness(state, unit, f); val a = activity(state, loc, unit, f)
        val m = mileage(state, loc, unit, f).body
        val (mn, mu) = OverviewMapper.splitUnit(m.hero)
        val extras = app.cairn.widget.OverviewExtras(
            listOf(
                OverviewItem(OverviewId.HeartRate, "70", "bpm"), OverviewItem(OverviewId.Hrv, "65", "ms"),
                OverviewItem(OverviewId.WeeklyMileage, mn, mu), OverviewItem(OverviewId.WeeklyLoad, "412", ""),
            ), f,
        )
        return OverviewMapper.build(t, s, r, l, fi, a, extras)
    }

    // ---- HRV ----
    private fun hrv(state: DataState, loc: Locale, f: FooterUi): WidgetUi.Content<HrvUi> {
        val ms = when (state) { DataState.Zero -> List(7) { 0 }; DataState.Huge -> List(7) { 250 }; else -> listOf(52, 49, 55, 58, 47, 61, 65) }
        val nights = ms.mapIndexed { i, v -> HrvNight(letters(loc)[i], v.takeIf { state != DataState.Partial || i == 6 }, i == 6, "day ${i + 1}") }
        val avg = ms.last()
        val ui = when (state) {
            DataState.LongText -> HrvUi("65", "ms", 65, "Überdurchschnittlich", Tone.Cool, "Normal range 46-58 ms", 46, 58, "Baseline 52 ms", 52, nights)
            DataState.Partial -> HrvUi("$avg", "ms", avg, "Normal", Tone.Good, null, null, null, null, null, nights)
            else -> HrvUi("$avg", "ms", avg, if (state == DataState.Zero) "Low" else "Elevated", Tone.Cool, "Normal range 46-58 ms", 46, 58, "Baseline 52 ms", 52, nights)
        }
        return content(ui, f, "Overnight HRV ${ui.hero}")
    }

    // ---- Heart rate ----
    private fun heart(state: DataState, f: FooterUi): WidgetUi.Content<HeartRateUi> {
        val ui = when (state) {
            DataState.Huge -> HeartRateUi("212", "bpm", "Resting HR", 212, 212, 212, 212, "7-day avg 212 bpm", "Daily average 212 bpm", "212-212 bpm", List(7) { 212 })
            DataState.Zero -> HeartRateUi("0", "bpm", "Resting HR", 0, 0, 0, 0, "7-day avg 0 bpm", "Daily average 0 bpm", "0-0 bpm", List(7) { 0 })
            DataState.Partial -> HeartRateUi("70", "bpm", "Daily average", null, 70, null, null, null, "Daily average 70 bpm", null, emptyList())
            else -> HeartRateUi("51", "bpm", "Resting HR", 51, 70, 44, 154, "7-day avg 53 bpm", "Daily average 70 bpm", "44-154 bpm", listOf(55, 54, 53, 54, 52, 53, 51))
        }
        return content(ui, f, "Resting heart rate ${ui.hero}")
    }

    // ---- Stress ----
    private fun stress(state: DataState, loc: Locale, f: FooterUi): WidgetUi.Content<StressUi> {
        val vals = when (state) { DataState.Zero -> List(7) { 0 }; DataState.Huge -> List(7) { 100 }; else -> listOf(24, 35, 28, 41, 22, 33, 30) }
        val bars = vals.mapIndexed { i, v -> StressBar(letters(loc)[i], v.takeIf { state != DataState.Partial || i == 6 }, v / 100f, WidgetMapper.stressTone(StressBand.of(v.coerceAtLeast(1))!!), i == 6, "day ${i + 1}") }
        val v = vals.last()
        val ui = StressUi("$v", v, if (state == DataState.LongText) "Überdurchschnittlich" else "Low", Tone.Good, if (state == DataState.Partial) null else "7-day avg $v", bars)
        return content(ui, f, "Stress ${ui.number}. ${ui.level}")
    }

    // ---- Weekly mileage ----
    private fun mileage(state: DataState, loc: Locale, unit: DistanceUnit, f: FooterUi): WidgetUi.Content<MileageUi> {
        val km = when (state) { DataState.Zero -> List(7) { 0.0 }; DataState.Huge -> listOf(150.0, 168.42, 99.99, 150.0, 150.0, 150.0, 131.58); else -> listOf(6.2, 0.0, 8.1, 0.0, 5.3, 5.5, 0.0) }
        val total = km.sum() * 1000; val peak = (km.max()).coerceAtLeast(1.0)
        val (n, u) = OverviewMapper.splitUnit(Fmt.distance(total, unit, loc))
        var run = 0.0
        val bars = km.mapIndexed { i, d -> MileageBar(letters(loc).let { l -> l[(i + 1) % 7] }, d * 1000, (d / peak).toFloat(), i == 5, "day ${i + 1}") }
        val cum = km.take(6).map { run += it; if (total > 0) (run * 1000 / total).toFloat() else 0f }
        val ui = MileageUi(n, u, total, if (state == DataState.Zero) 0 else 4, if (state == DataState.Zero) "0 activities" else "4 activities",
            if (state == DataState.Partial || state == DataState.Zero) null else if (state == DataState.Huge) "+999% vs last week" else "+12% vs last week", 12, bars, cum)
        return content(ui, f, "Weekly mileage ${ui.hero}", Screen.Activities)
    }

    /** The hero is style dependent: a style without a headline number (chips, stat grid, one-liner) has none to check. */
    fun expected(ui: WidgetUi<*>, loc: Locale, style: app.cairn.widget.WidgetStyle? = null): Expected = when (ui) {
        is WidgetUi.Content<*> -> contentExpected(ui, loc).let { e ->
            val b = ui.body
            val hero = when {
                style == app.cairn.widget.WidgetStyle.TodayStripe || style == app.cairn.widget.WidgetStyle.ActivityGrid || style == app.cairn.widget.WidgetStyle.ActivityLine -> null
                style == app.cairn.widget.WidgetStyle.FitnessGrid || style == app.cairn.widget.WidgetStyle.FitnessLadder -> null
                b is SleepUi && style == app.cairn.widget.WidgetStyle.SleepRing -> b.score?.toString() ?: b.durationText
                else -> e.hero
            }
            e.copy(hero = hero)
        }
        else -> Expected(false, null, emptyList())
    }

    private fun contentExpected(c: WidgetUi.Content<*>, loc: Locale): Expected {
        val tier = c.footer.tier
        return when (val b = c.body) {
            is TodayUi -> Expected(true, b.stepsText ?: "–", listOfNotNull(b.stepsText) + b.metrics.map { it.value }, metricValues = b.metrics.map { it.value }, tier = tier, description = c.description)
            is RecoveryUi -> Expected(true, b.percentText, listOf(b.percentText), tier = tier, description = c.description)
            is ActivityUi -> {
                val tv = listOfNotNull(b.distance, b.duration, b.pace, b.avgHr)
                Expected(true, b.distance ?: b.duration, tv + listOfNotNull(b.calories), tileValues = tv, tier = tier, description = c.description)
            }
            is TrainingLoadUi -> Expected(true, b.ratioText, listOfNotNull(b.ratioText, b.vo2Text), tier = tier, letters = b.bars.map { it.letter }, description = c.description)
            is SleepUi -> Expected(true, b.durationText, listOfNotNull(b.durationText, b.scoreText, b.window), tier = tier, description = c.description)
            is FitnessUi -> Expected(true, b.vo2Text ?: b.thresholdPace, listOfNotNull(b.vo2Text, b.thresholdPace) + b.predictions.map { it.time }, raceTimes = b.predictions.map { it.time }, tier = tier, description = c.description)
            is OverviewUi -> Expected(true, null, emptyList(), tier = tier, description = c.description)
            is HrvUi -> Expected(true, b.number, listOf(b.number), tier = tier, letters = b.nights.map { it.letter }, description = c.description)
            is HeartRateUi -> Expected(true, b.number, listOf(b.number), tier = tier, description = c.description)
            is StressUi -> Expected(true, b.number, listOf(b.number), tier = tier, letters = b.bars.map { it.letter }, description = c.description)
            is MileageUi -> Expected(true, b.number, listOf(b.number), tier = tier, letters = b.bars.map { it.letter }, description = c.description)
            else -> Expected(true, null, emptyList(), tier = tier, description = c.description)
        }
    }
}
