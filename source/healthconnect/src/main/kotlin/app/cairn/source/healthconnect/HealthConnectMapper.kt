package app.cairn.source.healthconnect

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Small input types so the mappers are pure and testable on the JVM. [lastModified] is the HC record's
// metadata.lastModifiedTime and becomes `sourceUpdatedAt`.
data class StepsInput(val start: Instant, val end: Instant, val count: Long, val lastModified: Instant)
data class HeartRateSampleInput(val time: Instant, val bpm: Long)
data class HeartRateInput(val samples: List<HeartRateSampleInput>, val lastModified: Instant)
data class RestingHrInput(val time: Instant, val bpm: Long, val lastModified: Instant)
data class DistanceInput(val start: Instant, val end: Instant, val meters: Double, val lastModified: Instant)
data class TotalCaloriesInput(val start: Instant, val end: Instant, val kcal: Double, val lastModified: Instant)

enum class SleepStageKind { AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, UNKNOWN }
data class SleepStageInput(val start: Instant, val end: Instant, val kind: SleepStageKind)
data class SleepSessionInput(val start: Instant, val end: Instant, val stages: List<SleepStageInput>, val lastModified: Instant)

data class ExerciseSessionInput(
    val id: String, val start: Instant, val end: Instant, val sport: String, val lastModified: Instant,
)

/** Maps Health Connect inputs to domain types. Days are local calendar days in [zone]. */
class HealthConnectMapper(private val zone: ZoneId = ZoneId.systemDefault()) {
    private fun day(t: Instant): LocalDate = t.atZone(zone).toLocalDate()
    private fun <T> sv(v: T, at: Instant) = SourcedValue(v, DataSource.HEALTH_CONNECT, at)
    private fun maxOf(a: Instant?, b: Instant) = if (a == null || b > a) b else a

    /** Records are attributed to the local day of their start (midnight-spanning records are not split). */
    fun dailySummaries(
        steps: List<StepsInput> = emptyList(),
        heartRate: List<HeartRateInput> = emptyList(),
        restingHr: List<RestingHrInput> = emptyList(),
        distance: List<DistanceInput> = emptyList(),
        calories: List<TotalCaloriesInput> = emptyList(),
    ): List<DailySummary> {
        val stepsBy = steps.groupBy { day(it.start) }
        val rhrBy = restingHr.groupBy { day(it.time) }
        val distBy = distance.groupBy { day(it.start) }
        val calBy = calories.groupBy { day(it.start) }
        val hrBy = heartRate.flatMap { r -> r.samples.map { it to r.lastModified } }.groupBy { day(it.first.time) }
        val days = (stepsBy.keys + rhrBy.keys + distBy.keys + calBy.keys + hrBy.keys).sorted()
        return days.map { d ->
            DailySummary(
                date = d,
                steps = stepsBy[d]?.let { sv(it.sumOf { s -> s.count }.toInt(), it.fold(null as Instant?) { a, s -> maxOf(a, s.lastModified) }!!) },
                // Latest reading of the day wins for resting HR.
                restingHr = rhrBy[d]?.maxByOrNull { it.time }?.let { sv(it.bpm.toInt(), it.lastModified) },
                avgHr = hrBy[d]?.let { l ->
                    sv(Math.round(l.map { it.first.bpm }.average()).toInt(), l.fold(null as Instant?) { a, s -> maxOf(a, s.second) }!!)
                },
                distanceMeters = distBy[d]?.let { sv(it.sumOf { x -> x.meters }, it.fold(null as Instant?) { a, s -> maxOf(a, s.lastModified) }!!) },
                calories = calBy[d]?.let { sv(Math.round(it.sumOf { x -> x.kcal }).toInt(), it.fold(null as Instant?) { a, s -> maxOf(a, s.lastModified) }!!) },
            )
        }
    }

    /** One [SleepNight] per wake-up day; when several sessions end on a day the longest is the main sleep. */
    fun sleepNights(sessions: List<SleepSessionInput>): List<SleepNight> =
        sessions.groupBy { day(it.end) }.toSortedMap().map { (d, list) ->
            val s = list.maxBy { Duration.between(it.start, it.end) }
            fun mins(kind: SleepStageKind) = s.stages.filter { it.kind == kind }.sumOf { Duration.between(it.start, it.end).toMinutes() }.toInt()
            val inBed = Duration.between(s.start, s.end).toMinutes().toInt()
            val hasStages = s.stages.isNotEmpty()
            val awake = if (hasStages) mins(SleepStageKind.AWAKE) + mins(SleepStageKind.OUT_OF_BED) else null
            SleepNight(
                date = d,
                stamp = Stamp(DataSource.HEALTH_CONNECT, list.maxOf { it.lastModified }),
                inBedMinutes = inBed,
                asleepMinutes = if (awake != null) (inBed - awake).coerceAtLeast(0) else inBed,
                deepMinutes = if (hasStages) mins(SleepStageKind.DEEP) else null,
                lightMinutes = if (hasStages) mins(SleepStageKind.LIGHT) + mins(SleepStageKind.SLEEPING) else null,
                remMinutes = if (hasStages) mins(SleepStageKind.REM) else null,
                awakeMinutes = awake,
                start = s.start, end = s.end,
            )
        }

    /** Exercise sessions with distance/calories from overlapping records, newest first. */
    fun activities(
        sessions: List<ExerciseSessionInput>,
        distance: List<DistanceInput> = emptyList(),
        calories: List<TotalCaloriesInput> = emptyList(),
    ): List<Activity> = sessions.sortedByDescending { it.start }.map { s ->
        fun overlaps(a: Instant, b: Instant) = a < s.end && b > s.start
        val dist = distance.filter { overlaps(it.start, it.end) }
        val cal = calories.filter { overlaps(it.start, it.end) }
        Activity(
            id = "hc-${s.id}",
            stamp = Stamp(DataSource.HEALTH_CONNECT, s.lastModified),
            date = day(s.start), sport = s.sport, start = s.start,
            durationSec = Duration.between(s.start, s.end).seconds.toInt(),
            distanceMeters = dist.takeIf { it.isNotEmpty() }?.sumOf { it.meters },
            calories = cal.takeIf { it.isNotEmpty() }?.let { Math.round(it.sumOf { c -> c.kcal }).toInt() },
        ).let { a ->
            val m = a.distanceMeters
            val dur = a.durationSec
            if (m != null && m > 0 && dur != null && dur > 0) a.copy(avgPaceSecPerKm = Math.round(dur / (m / 1000)).toInt()) else a
        }
    }
}
