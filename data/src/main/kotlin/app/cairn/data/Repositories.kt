package app.cairn.data

import app.cairn.core.database.ActivityDao
import app.cairn.core.database.DailyDao
import app.cairn.core.database.FitnessDao
import app.cairn.core.database.RecoveryDao
import app.cairn.core.database.SleepDao
import app.cairn.core.database.TrainingLoadDao
import app.cairn.core.database.toEntities
import app.cairn.core.database.toEntity
import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.FreshValue
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositories expose merged, freshness-annotated Flows over the Room cache and accept per-source writes.
 * Freshness is computed at emission time from [clock]; collectors that must tick while idle should
 * re-collect periodically (the widgets recompute on every update anyway).
 */
fun interface Clock { fun now(): Instant }

class DailyRepository(private val dao: DailyDao, private val clock: Clock = Clock { Instant.now() }) {
    /** One merged summary per day in [from]..[to] (ascending). Freshness follows the newest field. */
    fun observe(from: LocalDate, to: LocalDate): Flow<List<FreshValue<DailySummary>>> =
        dao.observeRange(from.toString(), to.toString()).map { rows ->
            val now = clock.now()
            rows.map { it.toDomain() }.groupBy { it.date }.toSortedMap().mapNotNull { (_, l) ->
                val m = Merge.daily(l) ?: return@mapNotNull null
                val stamp = Merge.dailyStamp(m) ?: Stamp(l.first().steps?.source ?: app.cairn.core.model.DataSource.COROS_MCP, Instant.EPOCH)
                Merge.fresh(m, stamp, now)
            }
        }

    fun observeDay(date: LocalDate): Flow<FreshValue<DailySummary>?> = observe(date, date).map { it.firstOrNull() }

    /** Stores source summaries; a same-source row already cached is coalesced field by field (newer wins). */
    suspend fun store(summaries: List<DailySummary>) {
        val rows = summaries.flatMap { it.toEntities() }
        if (rows.isEmpty()) return
        val out = rows.map { new ->
            val old = dao.get(listOf(new.date), new.source).firstOrNull()?.toDomain()
            Merge.coalesceDaily(old, new.toDomain()).toEntities().single { it.source == new.source }
        }
        dao.upsert(out)
    }
}

class SleepRepository(private val dao: SleepDao, private val clock: Clock = Clock { Instant.now() }) {
    fun observe(from: LocalDate, to: LocalDate): Flow<List<FreshValue<SleepNight>>> =
        dao.observeRange(from.toString(), to.toString()).map { rows ->
            val now = clock.now()
            rows.map { it.toDomain() }.groupBy { it.date }.toSortedMap().mapNotNull { (_, l) ->
                // Freshness follows the preferred record (MCP if present), not a gap-filling HC row.
                Merge.sleep(l)?.let { m -> Merge.fresh(m, m.stamp, now) }
            }
        }

    /** Latest night (by date) in the last [days] days. */
    fun observeLatest(today: LocalDate, days: Long = 7): Flow<FreshValue<SleepNight>?> =
        observe(today.minusDays(days), today).map { it.lastOrNull() }

    suspend fun store(nights: List<SleepNight>) {
        if (nights.isEmpty()) return
        val out = nights.map { n ->
            val old = dao.get(listOf(n.date.toString()), n.stamp.source.name).firstOrNull()?.toDomain()
            Merge.coalesceSleep(old, n).toEntity()
        }
        dao.upsert(out)
    }
}

class RecoveryRepository(private val dao: RecoveryDao, private val clock: Clock = Clock { Instant.now() }) {
    fun observe(): Flow<FreshValue<Recovery>?> = dao.observeAll().map { rows ->
        Merge.recovery(rows.map { it.toDomain() })?.let { Merge.fresh(it, it.stamp, clock.now()) }
    }

    suspend fun store(recovery: Recovery) = dao.upsert(recovery.toEntity())
}

class TrainingLoadRepository(private val dao: TrainingLoadDao, private val clock: Clock = Clock { Instant.now() }) {
    fun observe(from: LocalDate, to: LocalDate): Flow<List<FreshValue<TrainingLoadDay>>> =
        dao.observeRange(from.toString(), to.toString()).map { rows ->
            val now = clock.now()
            rows.map { it.toDomain() }.groupBy { it.date }.toSortedMap().mapNotNull { (_, l) ->
                Merge.trainingLoad(l)?.let { Merge.fresh(it, it.stamp, now) }
            }
        }

    fun observeLatest(today: LocalDate, days: Long = 14): Flow<FreshValue<TrainingLoadDay>?> =
        observe(today.minusDays(days), today).map { it.lastOrNull() }

    suspend fun store(days: List<TrainingLoadDay>) { if (days.isNotEmpty()) dao.upsert(days.map { it.toEntity() }) }
}

class FitnessRepository(private val dao: FitnessDao, private val clock: Clock = Clock { Instant.now() }) {
    fun observe(): Flow<FreshValue<FitnessSnapshot>?> = dao.observeAll().map { rows ->
        Merge.fitness(rows.map { it.toDomain() })?.let { Merge.fresh(it, it.stamp, clock.now()) }
    }

    suspend fun store(snapshot: FitnessSnapshot) = dao.upsert(snapshot.toEntity())
}

class ActivityRepository(private val dao: ActivityDao, private val clock: Clock = Clock { Instant.now() }) {
    /** Merged, de-duplicated activities since [since], newest first. */
    fun observeRecent(since: LocalDate, limit: Int = 20): Flow<List<FreshValue<Activity>>> =
        dao.observeSince(since.toString()).map { rows ->
            val now = clock.now()
            Merge.activities(rows.map { it.toDomain() }).take(limit).map { Merge.fresh(it, it.stamp, now) }
        }

    suspend fun store(activities: List<Activity>) { if (activities.isNotEmpty()) dao.upsert(activities.map { it.toEntity() }) }
}
