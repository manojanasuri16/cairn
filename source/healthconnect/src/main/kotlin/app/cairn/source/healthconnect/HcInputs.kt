package app.cairn.source.healthconnect

/** Everything one Health Connect window read returns, already converted to the pure mapper inputs. */
data class HcInputs(
    val steps: List<StepsInput> = emptyList(),
    val heartRate: List<HeartRateInput> = emptyList(),
    val restingHr: List<RestingHrInput> = emptyList(),
    val distance: List<DistanceInput> = emptyList(),
    val calories: List<TotalCaloriesInput> = emptyList(),
    val sleep: List<SleepSessionInput> = emptyList(),
    val exercise: List<ExerciseSessionInput> = emptyList(),
)
