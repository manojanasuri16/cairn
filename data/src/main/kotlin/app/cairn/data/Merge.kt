package app.cairn.data

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.FreshValue
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import app.cairn.core.model.newest
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Pure merge rules (see docs/plan.md section 4.7):
 *  - Daily: per field, newest `sourceUpdatedAt` wins; ties go to COROS_MCP. So Health Connect fills
 *    steps/HR when MCP is older, and MCP-only fields (stress, floors) always come from MCP.
 *  - Sleep, Recovery, TrainingLoad, Fitness: COROS_MCP preferred wholesale; Health Connect only fills
 *    fields MCP left null (sleep) or supplies the record when MCP has none.
 *  - Activities: union; duplicates (same day and start within 5 min, or same duration within 90 s when a
 *    start is unknown) collapse to the MCP record with HC filling null fields.
 */
object Merge {
    fun daily(rows: List<DailySummary>): DailySummary? {
        if (rows.isEmpty()) return null
        fun <T : Any> pick(sel: (DailySummary) -> SourcedValue<T>?): SourcedValue<T>? =
            rows.map(sel).reduceOrNull { a, b -> newest(a, b) }
        return DailySummary(
            date = rows.first().date,
            steps = pick { it.steps }, calories = pick { it.calories }, exerciseMinutes = pick { it.exerciseMinutes },
            floors = pick { it.floors }, avgStress = pick { it.avgStress }, restingHr = pick { it.restingHr },
            avgHr = pick { it.avgHr }, distanceMeters = pick { it.distanceMeters },
        )
    }

    /** Newest field stamp of a daily summary, or null when it has no values. */
    fun dailyStamp(d: DailySummary): Stamp? =
        listOfNotNull(d.steps, d.calories, d.exerciseMinutes, d.floors, d.avgStress, d.restingHr, d.avgHr, d.distanceMeters)
            .maxByOrNull { it.sourceUpdatedAt }?.stamp

    /** Same-source write coalescing: the newer field wins, the incoming one on a tie. */
    fun coalesceDaily(old: DailySummary?, new: DailySummary): DailySummary {
        if (old == null) return new
        fun <T : Any> pick(sel: (DailySummary) -> SourcedValue<T>?): SourcedValue<T>? {
            val o = sel(old); val n = sel(new)
            return when {
                o == null -> n
                n == null -> o
                n.sourceUpdatedAt >= o.sourceUpdatedAt -> n
                else -> o
            }
        }
        return new.copy(
            steps = pick { it.steps }, calories = pick { it.calories }, exerciseMinutes = pick { it.exerciseMinutes },
            floors = pick { it.floors }, avgStress = pick { it.avgStress }, restingHr = pick { it.restingHr },
            avgHr = pick { it.avgHr }, distanceMeters = pick { it.distanceMeters },
        )
    }

    /** Same-source write coalescing for sleep (daily summary and overview both write COROS_MCP rows). */
    fun coalesceSleep(old: SleepNight?, new: SleepNight): SleepNight = if (old == null) new else fill(new, old)

    /** [primary] with null fields taken from [other]. The stamp stays [primary]'s. */
    private fun fill(primary: SleepNight, other: SleepNight) = primary.copy(
        score = primary.score ?: other.score, asleepMinutes = primary.asleepMinutes ?: other.asleepMinutes,
        inBedMinutes = primary.inBedMinutes ?: other.inBedMinutes, deepMinutes = primary.deepMinutes ?: other.deepMinutes,
        lightMinutes = primary.lightMinutes ?: other.lightMinutes, remMinutes = primary.remMinutes ?: other.remMinutes,
        awakeMinutes = primary.awakeMinutes ?: other.awakeMinutes, avgHr = primary.avgHr ?: other.avgHr,
        minHr = primary.minHr ?: other.minHr, maxHr = primary.maxHr ?: other.maxHr,
        start = primary.start ?: other.start, end = primary.end ?: other.end,
    )

    fun sleep(rows: List<SleepNight>): SleepNight? {
        val mcp = rows.firstOrNull { it.stamp.source == DataSource.COROS_MCP }
        val hc = rows.firstOrNull { it.stamp.source == DataSource.HEALTH_CONNECT }
        return when {
            mcp != null && hc != null -> fill(mcp, hc)
            else -> mcp ?: hc
        }
    }

    /** MCP record if present, else the newest by stamp. */
    private fun <T> preferMcp(rows: List<T>, stamp: (T) -> Stamp): T? =
        rows.firstOrNull { stamp(it).source == DataSource.COROS_MCP } ?: rows.maxByOrNull { stamp(it).sourceUpdatedAt }

    fun recovery(rows: List<Recovery>): Recovery? = preferMcp(rows) { it.stamp }
    fun fitness(rows: List<FitnessSnapshot>): FitnessSnapshot? = preferMcp(rows) { it.stamp }
    fun trainingLoad(rows: List<TrainingLoadDay>): TrainingLoadDay? = preferMcp(rows) { it.stamp }

    private val START_TOLERANCE = Duration.ofMinutes(5)

    private fun isDuplicate(a: Activity, b: Activity): Boolean {
        if (a.date != b.date) return false
        val sa = a.start; val sb = b.start
        if (sa != null && sb != null) return Duration.between(sa, sb).abs() <= START_TOLERANCE
        val da = a.durationSec; val db = b.durationSec
        return da != null && db != null && abs(da - db) <= 90
    }

    fun activities(rows: List<Activity>): List<Activity> {
        val ordered = rows.sortedWith(compareBy({ it.stamp.source != DataSource.COROS_MCP }, { it.id }))
        val out = mutableListOf<Activity>()
        for (a in ordered) {
            val i = out.indexOfFirst { isDuplicate(it, a) }
            if (i < 0) out += a else out[i] = out[i].let { k ->
                k.copy(
                    start = k.start ?: a.start, durationSec = k.durationSec ?: a.durationSec,
                    distanceMeters = k.distanceMeters ?: a.distanceMeters, avgPaceSecPerKm = k.avgPaceSecPerKm ?: a.avgPaceSecPerKm,
                    avgHr = k.avgHr ?: a.avgHr, calories = k.calories ?: a.calories,
                )
            }
        }
        return out.sortedWith(compareByDescending<Activity> { it.date }.thenByDescending { it.start ?: Instant.EPOCH })
    }

    /** Wraps [value] with freshness computed from [stamp] at [now]. */
    fun <T> fresh(value: T, stamp: Stamp, now: Instant): FreshValue<T> =
        FreshValue(value, stamp.source, stamp.sourceUpdatedAt, stamp.freshness(now))

}
