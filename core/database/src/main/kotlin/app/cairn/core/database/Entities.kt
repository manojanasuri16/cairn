package app.cairn.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Instant
import java.time.LocalDate

// The cache stores RAW per-source rows (one row per day/entity per source). Merging across sources is
// done by the repositories, never in SQL, so the rules stay unit-testable. Dates are ISO-8601 strings,
// instants are epoch millis, source is DataSource.name.

private fun Long.inst(): Instant = Instant.ofEpochMilli(this)
private fun stampOf(source: String, at: Long) = Stamp(DataSource.valueOf(source), at.inst())

/** Daily totals from one source; each field has its own `...At` (epoch millis) because HC records differ in age. */
@Entity(tableName = "daily", primaryKeys = ["date", "source"])
data class DailyEntity(
    val date: String,
    val source: String,
    val steps: Int? = null, val stepsAt: Long? = null,
    val calories: Int? = null, val caloriesAt: Long? = null,
    val exerciseMinutes: Int? = null, val exerciseMinutesAt: Long? = null,
    val floors: Int? = null, val floorsAt: Long? = null,
    val avgStress: Int? = null, val avgStressAt: Long? = null,
    val restingHr: Int? = null, val restingHrAt: Long? = null,
    val avgHr: Int? = null, val avgHrAt: Long? = null,
    val distanceMeters: Double? = null, val distanceMetersAt: Long? = null,
) {
    /** Only this row's own source: fields from the other source are not present here. */
    fun toDomain(): DailySummary {
        val src = DataSource.valueOf(source)
        fun <T : Any> f(v: T?, at: Long?) = if (v != null && at != null) SourcedValue(v, src, at.inst()) else null
        return DailySummary(
            date = LocalDate.parse(date),
            steps = f(steps, stepsAt), calories = f(calories, caloriesAt), exerciseMinutes = f(exerciseMinutes, exerciseMinutesAt),
            floors = f(floors, floorsAt), avgStress = f(avgStress, avgStressAt), restingHr = f(restingHr, restingHrAt),
            avgHr = f(avgHr, avgHrAt), distanceMeters = f(distanceMeters, distanceMetersAt),
        )
    }
}

/** Splits a (possibly mixed-source) summary into one entity per source. */
fun DailySummary.toEntities(): List<DailyEntity> {
    val all = listOfNotNull(steps, calories, exerciseMinutes, floors, avgStress, restingHr, avgHr, distanceMeters)
    return all.map { it.source }.distinct().map { src ->
        fun <T : Any> pick(v: SourcedValue<T>?) = v?.takeIf { it.source == src }
        val s = pick(steps); val c = pick(calories); val e = pick(exerciseMinutes); val fl = pick(floors)
        val st = pick(avgStress); val r = pick(restingHr); val h = pick(avgHr); val d = pick(distanceMeters)
        DailyEntity(
            date = date.toString(), source = src.name,
            steps = s?.value, stepsAt = s?.sourceUpdatedAt?.toEpochMilli(),
            calories = c?.value, caloriesAt = c?.sourceUpdatedAt?.toEpochMilli(),
            exerciseMinutes = e?.value, exerciseMinutesAt = e?.sourceUpdatedAt?.toEpochMilli(),
            floors = fl?.value, floorsAt = fl?.sourceUpdatedAt?.toEpochMilli(),
            avgStress = st?.value, avgStressAt = st?.sourceUpdatedAt?.toEpochMilli(),
            restingHr = r?.value, restingHrAt = r?.sourceUpdatedAt?.toEpochMilli(),
            avgHr = h?.value, avgHrAt = h?.sourceUpdatedAt?.toEpochMilli(),
            distanceMeters = d?.value, distanceMetersAt = d?.sourceUpdatedAt?.toEpochMilli(),
        )
    }
}

@Entity(tableName = "sleep", primaryKeys = ["date", "source"])
data class SleepEntity(
    val date: String,
    val source: String,
    val updatedAt: Long,
    val score: Int? = null,
    val asleepMinutes: Int? = null,
    val inBedMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
) {
    fun toDomain() = SleepNight(
        LocalDate.parse(date), stampOf(source, updatedAt), score, asleepMinutes, inBedMinutes, deepMinutes, lightMinutes,
        remMinutes, awakeMinutes, avgHr, minHr, maxHr, startMs?.inst(), endMs?.inst(),
    )
}

fun SleepNight.toEntity() = SleepEntity(
    date.toString(), stamp.source.name, stamp.sourceUpdatedAt.toEpochMilli(), score, asleepMinutes, inBedMinutes,
    deepMinutes, lightMinutes, remMinutes, awakeMinutes, avgHr, minHr, maxHr, start?.toEpochMilli(), end?.toEpochMilli(),
)

@Entity(tableName = "recovery")
data class RecoveryEntity(
    @PrimaryKey val source: String,
    val updatedAt: Long,
    val percent: Int? = null,
    val level: String? = null,
    val hoursToFullRecovery: Int? = null,
) {
    fun toDomain() = Recovery(stampOf(source, updatedAt), percent, level, hoursToFullRecovery)
}

fun Recovery.toEntity() = RecoveryEntity(stamp.source.name, stamp.sourceUpdatedAt.toEpochMilli(), percent, level, hoursToFullRecovery)

@Entity(tableName = "training_load", primaryKeys = ["date", "source"])
data class TrainingLoadEntity(
    val date: String,
    val source: String,
    val updatedAt: Long,
    val shortTermLoad: Int? = null,
    val longTermLoad: Int? = null,
    val loadRatio: Double? = null,
    val comment: String? = null,
) {
    fun toDomain() = TrainingLoadDay(LocalDate.parse(date), stampOf(source, updatedAt), shortTermLoad, longTermLoad, loadRatio, comment)
}

fun TrainingLoadDay.toEntity() =
    TrainingLoadEntity(date.toString(), stamp.source.name, stamp.sourceUpdatedAt.toEpochMilli(), shortTermLoad, longTermLoad, loadRatio, comment)

@Entity(tableName = "fitness")
data class FitnessEntity(
    @PrimaryKey val source: String,
    val updatedAt: Long,
    val vo2max: Int? = null,
    val runningLevel: Int? = null,
    val thresholdPaceSecPerKm: Int? = null,
    val prediction5kSec: Int? = null,
    val prediction10kSec: Int? = null,
    val predictionHalfSec: Int? = null,
    val predictionMarathonSec: Int? = null,
) {
    fun toDomain() = FitnessSnapshot(
        stampOf(source, updatedAt), vo2max, runningLevel, thresholdPaceSecPerKm,
        prediction5kSec, prediction10kSec, predictionHalfSec, predictionMarathonSec,
    )
}

fun FitnessSnapshot.toEntity() = FitnessEntity(
    stamp.source.name, stamp.sourceUpdatedAt.toEpochMilli(), vo2max, runningLevel, thresholdPaceSecPerKm,
    prediction5kSec, prediction10kSec, predictionHalfSec, predictionMarathonSec,
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val source: String,
    val updatedAt: Long,
    val date: String,
    val sport: String,
    val startMs: Long? = null,
    val durationSec: Int? = null,
    val distanceMeters: Double? = null,
    val avgPaceSecPerKm: Int? = null,
    val avgHr: Int? = null,
    val calories: Int? = null,
    val location: String? = null,
    val sportType: Int? = null,
) {
    fun toDomain() = Activity(
        id, stampOf(source, updatedAt), LocalDate.parse(date), sport, startMs?.inst(), durationSec, distanceMeters,
        avgPaceSecPerKm, avgHr, calories, location, sportType,
    )
}

fun Activity.toEntity() = ActivityEntity(
    id, stamp.source.name, stamp.sourceUpdatedAt.toEpochMilli(), date.toString(), sport, start?.toEpochMilli(),
    durationSec, distanceMeters, avgPaceSecPerKm, avgHr, calories, location, sportType,
)
