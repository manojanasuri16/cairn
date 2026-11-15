package app.cairn.widget

import app.cairn.core.model.Freshness

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

    fun recovery() = content(RecoveryUi("82%", 0.82f, "Well recovered", "Full in 4h"), "Recovery 82 percent. Well recovered. Full in 4 hours.")

    fun activity() = content(
        ActivityUi("Running", "Today", "10.2 km", "52m", "5:07 /km", "152 bpm"),
        "Last activity. Running. Today. 10.2 kilometres. 52 minutes.", Screen.Activities,
    )

    fun load() = content(
        TrainingLoadUi(
            listOf(0.4f, 0.65f, 0f, 0.8f, 0.55f, 1f, 0.7f).mapIndexed { i, f -> LoadBar("MTWTFSS"[i].toString(), f, i == 6, "day ${i + 1}") },
            "1.12", "Optimal", "VO2max 52", "+1", 1.12,
        ),
        "Training load. Load ratio 1.12. Optimal. VO2max 52.", Screen.Fitness,
    )

    fun sleep() = content(
        SleepUi(
            "7h 41m", "Score 84", "23:10–07:02",
            listOf(
                StageSlice(SleepStage.Deep, 90, "Deep 1h 30m"), StageSlice(SleepStage.Light, 250, "Light 4h 10m"),
                StageSlice(SleepStage.Rem, 100, "REM 1h 40m"), StageSlice(SleepStage.Awake, 21, "Awake 21m"),
            ), null, 461, 84,
        ),
        "Sleep. Slept 7 hours 41 minutes. Score 84.",
    )

    fun fitness() = content(
        FitnessUi("52", "4:21 /km", listOf(RacePrediction("5K", "21:40"), RacePrediction("10K", "45:10"), RacePrediction("HM", "1:39:30"), RacePrediction("M", "3:30:12"))),
        "Fitness. VO2max 52. Threshold pace 4:21 per kilometre.", Screen.Fitness,
    )

    fun overview() = content(
        OverviewUi(
            listOf(
                OverviewItem(OverviewId.Steps, "8,432", "steps", 0.84f),
                OverviewItem(OverviewId.Sleep, "7h 41m", "", 0.96f),
                OverviewItem(OverviewId.Recovery, "82", "%", 0.82f),
                OverviewItem(OverviewId.RestingHr, "52", "bpm"),
                OverviewItem(OverviewId.LoadRatio, "1.12", "", null, 1.12, listOf(0.4f, 0.65f, 0.3f, 0.8f, 0.55f, 1f, 0.7f)),
                OverviewItem(OverviewId.Calories, "1,820", "kcal"),
            ),
        ),
        "Overview. Steps 8,432. Sleep 7 hours 41 minutes. Recovery 82 percent. Resting heart rate 52.",
    )
}
