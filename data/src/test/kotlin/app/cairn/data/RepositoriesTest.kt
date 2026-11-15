package app.cairn.data

import app.cairn.core.database.ActivityDao
import app.cairn.core.database.ActivityEntity
import app.cairn.core.database.DailyDao
import app.cairn.core.database.DailyEntity
import app.cairn.core.database.FitnessDao
import app.cairn.core.database.FitnessEntity
import app.cairn.core.database.RecoveryDao
import app.cairn.core.database.RecoveryEntity
import app.cairn.core.database.SleepDao
import app.cairn.core.database.SleepEntity
import app.cairn.core.database.TrainingLoadDao
import app.cairn.core.database.TrainingLoadEntity
import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Freshness
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDailyDao : DailyDao {
    val rows = MutableStateFlow<List<DailyEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeRange(from: String, to: String): Flow<List<DailyEntity>> = rows.map { l -> l.filter { it.date in from..to }.sortedBy { it.date } }
    override suspend fun get(dates: List<String>, source: String) = rows.value.filter { it.date in dates && it.source == source }
    override suspend fun upsert(rows: List<DailyEntity>) { this.rows.value = this.rows.value.filterNot { o -> rows.any { it.date == o.date && it.source == o.source } } + rows }
    override suspend fun deleteBefore(before: String) { rows.value = rows.value.filter { it.date >= before } }
}

private class FakeSleepDao : SleepDao {
    val rows = MutableStateFlow<List<SleepEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeRange(from: String, to: String) = rows.map { l -> l.filter { it.date in from..to } }
    override suspend fun get(dates: List<String>, source: String) = rows.value.filter { it.date in dates && it.source == source }
    override suspend fun upsert(rows: List<SleepEntity>) { this.rows.value = this.rows.value.filterNot { o -> rows.any { it.date == o.date && it.source == o.source } } + rows }
    override suspend fun deleteBefore(before: String) { rows.value = rows.value.filter { it.date >= before } }
}

private class FakeRecoveryDao : RecoveryDao {
    val rows = MutableStateFlow<List<RecoveryEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeAll() = rows
    override suspend fun upsert(row: RecoveryEntity) { rows.value = rows.value.filter { it.source != row.source } + row }
}

private class FakeLoadDao : TrainingLoadDao {
    val rows = MutableStateFlow<List<TrainingLoadEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeRange(from: String, to: String) = rows.map { l -> l.filter { it.date in from..to } }
    override suspend fun upsert(rows: List<TrainingLoadEntity>) { this.rows.value = this.rows.value.filterNot { o -> rows.any { it.date == o.date && it.source == o.source } } + rows }
    override suspend fun deleteBefore(before: String) { rows.value = rows.value.filter { it.date >= before } }
}

private class FakeFitnessDao : FitnessDao {
    val rows = MutableStateFlow<List<FitnessEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeAll() = rows
    override suspend fun upsert(row: FitnessEntity) { rows.value = rows.value.filter { it.source != row.source } + row }
}

private class FakeActivityDao : ActivityDao {
    val rows = MutableStateFlow<List<ActivityEntity>>(emptyList())
    override suspend fun clear() { rows.value = emptyList() }
    override fun observeSince(from: String) = rows.map { l -> l.filter { it.date >= from } }
    override suspend fun upsert(rows: List<ActivityEntity>) { this.rows.value = this.rows.value.filterNot { o -> rows.any { it.id == o.id } } + rows }
    override suspend fun deleteBefore(before: String) { rows.value = rows.value.filter { it.date >= before } }
}

class ClearTest {
    @Test fun clearEmptiesRepositories() = runBlocking {
        val dao = FakeActivityDao()
        val repo = ActivityRepository(dao)
        val at = Instant.parse("2030-01-03T10:00:00Z")
        repo.store(listOf(app.cairn.core.model.Activity("a", app.cairn.core.model.Stamp(app.cairn.core.model.DataSource.COROS_MCP, at), LocalDate.of(2030, 1, 3), "run")))
        assertEquals(1, dao.rows.value.size)
        repo.clear()
        assertTrue(dao.rows.value.isEmpty())
        val sleep = FakeSleepDao()
        SleepRepository(sleep).clear()
        assertTrue(sleep.rows.value.isEmpty())
    }
}

class RepositoriesTest {
    private val day = LocalDate.of(2030, 1, 3)
    private val now = Instant.parse("2030-01-03T12:00:00Z")
    private val clock = Clock { now }
    private fun at(h: Int) = Instant.parse("2030-01-03T%02d:00:00Z".format(h))
    private fun mcp(h: Int) = Stamp(DataSource.COROS_MCP, at(h))
    private fun hc(h: Int) = Stamp(DataSource.HEALTH_CONNECT, at(h))
    private fun <T> sv(v: T, s: Stamp) = SourcedValue(v, s.source, s.sourceUpdatedAt)

    // ---- daily ----
    @Test fun dailyNewestPerFieldWins() = runBlocking {
        val repo = DailyRepository(FakeDailyDao(), clock)
        repo.store(listOf(DailySummary(day, steps = sv(3000, mcp(6)), avgStress = sv(30, mcp(6)), restingHr = sv(52, mcp(6)))))
        repo.store(listOf(DailySummary(day, steps = sv(5000, hc(10)), restingHr = sv(50, hc(5)), avgHr = sv(70, hc(10)))))
        val m = repo.observeDay(day).first()!!
        assertEquals(5000, m.value.steps!!.value)           // HC newer than MCP
        assertEquals(DataSource.HEALTH_CONNECT, m.value.steps!!.source)
        assertEquals(52, m.value.restingHr!!.value)         // MCP newer than HC
        assertEquals(30, m.value.avgStress!!.value)         // MCP only
        assertEquals(70, m.value.avgHr!!.value)             // HC only
        assertEquals(at(10), m.sourceUpdatedAt)
        assertEquals(Freshness.Fresh, m.freshness)          // 2h old at 12:00 < 3h
    }

    @Test fun dailyTieGoesToMcpAndMcpOlderIsOverridden() = runBlocking {
        val repo = DailyRepository(FakeDailyDao(), clock)
        repo.store(listOf(DailySummary(day, steps = sv(1, hc(8)), calories = sv(100, mcp(2)))))
        repo.store(listOf(DailySummary(day, steps = sv(2, mcp(8)), calories = sv(90, hc(9)))))
        val m = repo.observeDay(day).first()!!
        assertEquals(2, m.value.steps!!.value)
        assertEquals(90, m.value.calories!!.value)
    }

    @Test fun dailyStaleFreshness() = runBlocking {
        val repo = DailyRepository(FakeDailyDao(), clock)
        repo.store(listOf(DailySummary(day, steps = sv(1, hc(0)))))
        assertEquals(Freshness.Stale, repo.observeDay(day).first()!!.freshness) // 12h old
    }

    @Test fun dailySameSourcePartialWriteKeepsOtherFields() = runBlocking {
        val repo = DailyRepository(FakeDailyDao(), clock)
        repo.store(listOf(DailySummary(day, steps = sv(100, hc(8)))))
        repo.store(listOf(DailySummary(day, avgHr = sv(66, hc(9)))))
        repo.store(listOf(DailySummary(day, steps = sv(50, hc(7))))) // older: ignored
        val m = repo.observeDay(day).first()!!.value
        assertEquals(100, m.steps!!.value); assertEquals(66, m.avgHr!!.value)
    }

    @Test fun dailyMissingDayIsNull() = runBlocking {
        assertNull(DailyRepository(FakeDailyDao(), clock).observeDay(day).first())
    }

    // ---- sleep ----
    @Test fun sleepMcpPreferredHcFillsGaps() = runBlocking {
        val repo = SleepRepository(FakeSleepDao(), clock)
        repo.store(listOf(SleepNight(day, hc(7), asleepMinutes = 470, start = at(0), end = at(7), deepMinutes = 60)))
        repo.store(listOf(SleepNight(day, mcp(6), score = 80, asleepMinutes = 450)))
        val s = repo.observeLatest(day).first()!!
        assertEquals(80, s.value.score)
        assertEquals(450, s.value.asleepMinutes)   // MCP wins even though HC is newer
        assertEquals(60, s.value.deepMinutes)      // filled from HC
        assertEquals(at(0), s.value.start)
        assertEquals(DataSource.COROS_MCP, s.source)
        assertEquals(at(6), s.sourceUpdatedAt)
    }

    @Test fun sleepHcOnlyAndMcpWritesCoalesce() = runBlocking {
        val repo = SleepRepository(FakeSleepDao(), clock)
        repo.store(listOf(SleepNight(day, hc(7), asleepMinutes = 400)))
        assertEquals(DataSource.HEALTH_CONNECT, repo.observeLatest(day).first()!!.source)
        repo.store(listOf(SleepNight(day, mcp(5), deepMinutes = 70, inBedMinutes = 480)))   // daily summary (no score)
        repo.store(listOf(SleepNight(day, mcp(6), score = 77, asleepMinutes = 410)))        // overview
        val s = repo.observeLatest(day).first()!!.value
        assertEquals(77, s.score); assertEquals(70, s.deepMinutes); assertEquals(480, s.inBedMinutes); assertEquals(410, s.asleepMinutes)
    }

    // ---- recovery / load / fitness ----
    @Test fun recoveryPrefersMcpThenNewest() = runBlocking {
        val dao = FakeRecoveryDao()
        val repo = RecoveryRepository(dao, clock)
        assertNull(repo.observe().first())
        repo.store(Recovery(hc(11), percent = 10))
        assertEquals(10, repo.observe().first()!!.value.percent)
        repo.store(Recovery(mcp(4), percent = 90, level = "Fresh"))
        val r = repo.observe().first()!!
        assertEquals(90, r.value.percent)
        assertEquals(Freshness.Aging, r.freshness) // 8h old
    }

    @Test fun trainingLoadAndFitness() = runBlocking {
        val lr = TrainingLoadRepository(FakeLoadDao(), clock)
        lr.store(listOf(TrainingLoadDay(day.minusDays(1), mcp(1), 5), TrainingLoadDay(day, mcp(11), 9, 40, 0.2)))
        val l = lr.observeLatest(day).first()!!
        assertEquals(day, l.value.date); assertEquals(Freshness.Fresh, l.freshness)
        assertEquals(2, lr.observe(day.minusDays(5), day).first().size)

        val fr = FitnessRepository(FakeFitnessDao(), clock)
        assertNull(fr.observe().first())
        fr.store(FitnessSnapshot(mcp(0), vo2max = 51))
        assertEquals(51, fr.observe().first()!!.value.vo2max)
        assertEquals(Freshness.Stale, fr.observe().first()!!.freshness)
    }

    // ---- activities ----
    @Test fun activitiesDedupeMcpWinsHcFills() = runBlocking {
        val repo = ActivityRepository(FakeActivityDao(), clock)
        repo.store(
            listOf(
                Activity("L1", mcp(9), day, "Outdoor Run", start = at(7), durationSec = 1800, avgHr = 150),
                Activity("hc-a", hc(9), day, "Run", start = at(7).plusSeconds(120), durationSec = 1795, distanceMeters = 5000.0),
                Activity("hc-b", hc(9), day, "Walk", start = at(15), durationSec = 600),
                Activity("hc-old", hc(9), day.minusDays(30), "Ride"),
            ),
        )
        val r = repo.observeRecent(day.minusDays(7)).first()
        assertEquals(listOf("hc-b", "L1"), r.map { it.value.id })
        val run = r.last().value
        assertEquals("Outdoor Run", run.sport)
        assertEquals(5000.0, run.distanceMeters!!, 1e-9)
        assertEquals(150, run.avgHr)
    }

    @Test fun activitiesDedupeByDurationWhenStartUnknownAndLimit() = runBlocking {
        val repo = ActivityRepository(FakeActivityDao(), clock)
        repo.store(
            listOf(
                Activity("L1", mcp(9), day, "Run", durationSec = 1800),
                Activity("hc-a", hc(9), day, "Run", start = at(7), durationSec = 1830),
                Activity("hc-b", hc(9), day, "Run", start = at(16), durationSec = 3000),
            ),
        )
        assertEquals(2, repo.observeRecent(day).first().size)
        assertEquals(1, repo.observeRecent(day, limit = 1).first().size)
        assertTrue(repo.observeRecent(day.plusDays(1)).first().isEmpty())
    }
}
