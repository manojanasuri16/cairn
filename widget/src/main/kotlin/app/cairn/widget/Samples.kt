package app.cairn.widget

import app.cairn.core.model.Freshness
import app.cairn.core.model.StressBand

/** Synthetic sample data for previews. Made up; never derived from the user's data. */
object Samples {
    val footer = FooterUi("Updated 5m ago", Freshness.Fresh, "5m ago", 5 * 60_000L)
    val staleFooter = FooterUi("Not updated for 2 days. Tap to sync", Freshness.Stale, "Stale", 2 * 86_400_000L)

    private fun <T> content(body: T, desc: String, screen: Screen = Screen.Today) = WidgetUi.Content(body, footer, desc, screen)

    fun today() = content(
        TodayUi("8,432", 0.84f, listOf(
            Metric(MetricId.Sleep, "Sleep", "7h 41m"), Metric(MetricId.RestingHr, "Resting HR", "52 bpm"),
            Metric(MetricId.Recovery, "Recovery", "82%"), Metric(MetricId.Calories, "Calories", "1,820"),
        )),
        "Today. 8,432 steps. Sleep 7 hours 41 minutes. Resting heart rate 52. Recovery 82 percent.",
    )

    fun recovery() = content(RecoveryUi("82%", 0.82f, "Well recovered", "4 hrs till full recovery"), "Recovery 82 percent. Well recovered. 4 hrs till full recovery.")

    fun activity() = content(
        ActivityUi("Running", "Today", "10.20 km", "52 min", "5'07\" /km", "152 bpm"),
        "Last activity. Running. Today. 10.2 kilometres. 52 minutes.", Screen.Activities,
    )

    fun load() = content(
        TrainingLoadUi(
            listOf(0.4f, 0.65f, 0f, 0.8f, 0.55f, 1f, 0.7f).mapIndexed { i, f -> LoadBar("MTWTFSS"[i].toString(), f, i == 6, "day ${i + 1}") },
            "112%", "Optimal", "VO2max 52", "+1", 1.12,
        ),
        "Training load. Intensity Trend 112 percent. Optimal. VO2max 52.", Screen.Fitness,
    )

    fun sleep() = content(
        SleepUi(
            "7 h 41 min", "Score 84", "23:10–07:02",
            listOf(
                StageSlice(SleepStage.Deep, 90, "Deep 1 h 30 min"), StageSlice(SleepStage.Light, 250, "Light 4 h 10 min"),
                StageSlice(SleepStage.Rem, 100, "REM 1 h 40 min"), StageSlice(SleepStage.Awake, 21, "Awake 21 min"),
            ), null, 461, 84,
        ),
        "Sleep. Slept 7 hours 41 minutes. Score 84.",
    )

    fun fitness() = content(
        FitnessUi("52", "4'21\" /km", listOf(RacePrediction("5K", "21:40"), RacePrediction("10K", "45:10"), RacePrediction("HM", "1:39:30"), RacePrediction("M", "3:30:12"))),
        "Fitness. VO2max 52. Threshold pace 4:21 per kilometre.", Screen.Fitness,
    )

    fun overview() = content(
        OverviewUi(
            listOf(
                OverviewItem(OverviewId.Steps, "8,432", "steps", 0.84f),
                OverviewItem(OverviewId.Sleep, "7 h 41 min", "", 0.96f),
                OverviewItem(OverviewId.Recovery, "82", "%", 0.82f),
                OverviewItem(OverviewId.RestingHr, "52", "bpm"),
                OverviewItem(OverviewId.LoadRatio, "112%", "", null, 1.12, listOf(0.4f, 0.65f, 0.3f, 0.8f, 0.55f, 1f, 0.7f)),
                OverviewItem(OverviewId.Calories, "1,820", "kcal"),
            ),
        ),
        "Overview. Steps 8,432. Sleep 7 hours 41 minutes. Recovery 82 percent. Resting heart rate 52.",
    )

    private val letters = "MTWTFSS"

    fun hrv() = content(
        HrvUi(
            "65", "ms", 65, "Elevated", Tone.Cool, "Normal range 46-58 ms", 46, 58, "Baseline 52 ms", 52,
            listOf(52, 49, 55, 58, 47, 61, 65).mapIndexed { i, ms -> HrvNight(letters[i].toString(), ms, i == 6, "day ${i + 1} $ms milliseconds") },
        ),
        "Overnight HRV 65 milliseconds. Elevated. Normal range 46 to 58 milliseconds.",
    )

    fun heartRate() = content(
        HeartRateUi(
            "51", "bpm", "Resting HR", 51, 70, 44, 154, "7-day avg 53 bpm", "Daily average 70 bpm", "44-154 bpm",
            listOf(55, 54, 53, 54, 52, 53, 51),
        ),
        "Resting heart rate 51 bpm. Seven day average 53 bpm. Daily average 70 bpm.",
    )

    fun stress() = content(
        StressUi(
            "30", 30, "Low", Tone.Good, "7-day avg 30",
            listOf(24, 35, 28, 41, 22, 33, 30).mapIndexed { i, v -> StressBar(letters[i].toString(), v, v / 45f, WidgetMapper.stressTone(StressBand.of(v)!!), i == 6, "day ${i + 1} stress $v") },
        ),
        "Stress 30. Low. Seven day average 30.",
    )

    fun mileage(): WidgetUi.Content<MileageUi> {
        val km = listOf(6.2, 0.0, 8.1, 0.0, 5.3, 5.5, 0.0)
        val peak = km.max()
        val total = km.sum()
        var run = 0.0
        return WidgetUi.Content(
            MileageUi(
                "25.10", "km", total * 1000, 4, "4 activities", "+12% vs last week", 12,
                km.mapIndexed { i, d -> MileageBar(letters[i].toString(), d * 1000, (d / peak).toFloat(), i == 5, "day ${i + 1} $d km") },
                km.take(6).map { run += it; (run / total).toFloat() },
            ),
            footer, "Weekly mileage 25.10 kilometres this week. 4 activities. +12% vs last week.", Screen.Activities,
        )
    }
}
