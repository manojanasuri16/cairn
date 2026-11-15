package app.cairn.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.HrvDay
import app.cairn.core.model.VitalsDay
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseTest {
    private lateinit var db: CairnDatabase
    private val t1 = Instant.parse("2030-01-03T08:00:00Z")
    private val t2 = Instant.parse("2030-01-03T09:00:00Z")
    private val mcp = Stamp(DataSource.COROS_MCP, t1)
    private val hc = Stamp(DataSource.HEALTH_CONNECT, t2)
    private val d = LocalDate.of(2030, 1, 3)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CairnDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun dailyRoundTripAndSplitBySource() = runBlocking {
        val mixed = DailySummary(
            date = d,
            steps = SourcedValue(5000, DataSource.HEALTH_CONNECT, t2),
            avgStress = SourcedValue(30, DataSource.COROS_MCP, t1),
            restingHr = SourcedValue(52, DataSource.COROS_MCP, t1),
        )
        val rows = mixed.toEntities()
        assertEquals(2, rows.size)
        db.daily().upsert(rows)
        val back = db.daily().observeRange("2030-01-01", "2030-01-31").first().map { it.toDomain() }
        assertEquals(2, back.size)
        val hcRow = back.single { it.steps != null }
        assertEquals(5000, hcRow.steps!!.value)
        assertEquals(t2, hcRow.steps!!.sourceUpdatedAt)
        assertEquals(DataSource.HEALTH_CONNECT, hcRow.steps!!.source)
        assertNull(hcRow.avgStress)
        val mcpRow = back.single { it.avgStress != null }
        assertEquals(52, mcpRow.restingHr!!.value)
    }

    @Test fun dailyUpsertReplacesSameKey() = runBlocking {
        val a = DailySummary(d, steps = SourcedValue(1, DataSource.COROS_MCP, t1)).toEntities()
        val b = DailySummary(d, steps = SourcedValue(2, DataSource.COROS_MCP, t2)).toEntities()
        db.daily().upsert(a); db.daily().upsert(b)
        val rows = db.daily().get(listOf("2030-01-03"), "COROS_MCP")
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].steps)
    }

    @Test fun flowEmitsOnChange() = runBlocking {
        val flow = db.recovery().observeAll()
        assertEquals(0, flow.first().size)
        db.recovery().upsert(Recovery(mcp, 80, "Good", 4).toEntity())
        val r = flow.first().single().toDomain()
        assertEquals(80, r.percent); assertEquals("Good", r.level); assertEquals(mcp, r.stamp)
    }

    @Test fun sleepRoundTripAndRange() = runBlocking {
        val n = SleepNight(d, mcp, score = 80, asleepMinutes = 400, start = t1, end = t2)
        db.sleep().upsert(listOf(n.toEntity(), n.copy(date = LocalDate.of(2030, 3, 1)).toEntity()))
        val r = db.sleep().observeRange("2030-01-01", "2030-01-31").first()
        assertEquals(listOf(n), r.map { it.toDomain() })
        db.sleep().deleteBefore("2030-02-01")
        assertEquals(0, db.sleep().observeRange("2030-01-01", "2030-01-31").first().size)
    }

    @Test fun trainingLoadFitnessActivities() = runBlocking {
        val tl = TrainingLoadDay(d, mcp, 10, 20, 0.5, "Ok")
        db.trainingLoad().upsert(listOf(tl.toEntity()))
        assertEquals(tl, db.trainingLoad().observeRange("2030-01-01", "2030-01-31").first().single().toDomain())

        val f = FitnessSnapshot(mcp, vo2max = 50, thresholdPaceSecPerKm = 270, prediction5kSec = 1400)
        db.fitness().upsert(f.toEntity())
        assertEquals(f, db.fitness().observeAll().first().single().toDomain())

        val a1 = Activity("L1", mcp, d, "Run", start = t1, durationSec = 1800, distanceMeters = 5000.0, avgHr = 150)
        val a2 = Activity("hc-1", hc, d.minusDays(10), "Walk")
        db.activities().upsert(listOf(a1.toEntity(), a2.toEntity()))
        assertEquals(listOf(a1), db.activities().observeSince("2030-01-01").first().map { it.toDomain() })
        assertEquals(2, db.activities().observeSince("2020-01-01").first().size)
    }

    @Test fun newTablesRoundTrip() = runBlocking {
        val v = VitalsDay(d, mcp, avgHr = 70, minHr = 44, maxHr = 154, restingHr = 51, avgStress = 30)
        db.vitals().upsert(listOf(v.toEntity()))
        assertEquals(v, db.vitals().observeRange("2030-01-01", "2030-01-31").first().single().toDomain())
        val h = HrvDay(d, mcp, 65, "Above normal", 46, 58, 52)
        db.hrv().upsert(listOf(h.toEntity()))
        assertEquals(h, db.hrv().observeRange("2030-01-01", "2030-01-31").first().single().toDomain())
        val det = ActivityDetail("L1", mcp, 112, 3.1, 0.0, "Base")
        db.activityDetails().upsert(listOf(det.toEntity()))
        assertEquals(det, db.activityDetails().observeAll().first().single().toDomain())
        assertEquals(listOf("L1"), db.activityDetails().ids())
        // Refreshing the activity list must not erase the cached detail.
        db.activities().upsert(listOf(Activity("L1", mcp, d, "Run").toEntity()))
        assertEquals(1, db.activityDetails().ids().size)
    }

    @Test fun fitnessKeepsDecimalRunningLevel() = runBlocking {
        val f = FitnessSnapshot(mcp, runningLevel = 73.3)
        db.fitness().upsert(f.toEntity())
        assertEquals(f, db.fitness().observeAll().first().single().toDomain())
    }
}
