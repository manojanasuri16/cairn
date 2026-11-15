package app.cairn.source.healthconnect

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

// Thin adapters from real HC records to the pure mapper inputs (no logic worth testing beyond the mapper).

fun StepsRecord.toInput() = StepsInput(startTime, endTime, count, metadata.lastModifiedTime)

fun HeartRateRecord.toInput() = HeartRateInput(samples.map { HeartRateSampleInput(it.time, it.beatsPerMinute) }, metadata.lastModifiedTime)

fun RestingHeartRateRecord.toInput() = RestingHrInput(time, beatsPerMinute, metadata.lastModifiedTime)

fun DistanceRecord.toInput() = DistanceInput(startTime, endTime, distance.inMeters, metadata.lastModifiedTime)

fun TotalCaloriesBurnedRecord.toInput() = TotalCaloriesInput(startTime, endTime, energy.inKilocalories, metadata.lastModifiedTime)

fun SleepSessionRecord.toInput() = SleepSessionInput(
    startTime, endTime,
    stages.map { st ->
        SleepStageInput(
            st.startTime, st.endTime,
            when (st.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStageKind.AWAKE
                SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageKind.LIGHT
                SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageKind.DEEP
                SleepSessionRecord.STAGE_TYPE_REM -> SleepStageKind.REM
                SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageKind.SLEEPING
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageKind.OUT_OF_BED
                else -> SleepStageKind.UNKNOWN
            },
        )
    },
    metadata.lastModifiedTime,
)

fun ExerciseSessionRecord.toInput() = ExerciseSessionInput(
    id = metadata.id, start = startTime, end = endTime,
    sport = title?.takeIf { it.isNotBlank() } ?: sportName(exerciseType), lastModified = metadata.lastModifiedTime,
)

internal fun sportName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill Run"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor Ride"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open Water Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    else -> "Workout"
}
