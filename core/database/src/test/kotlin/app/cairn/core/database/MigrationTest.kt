package app.cairn.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds a real version 1 database from the exported schema, then lets Room migrate and validate it as version 2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    private fun createV1() {
        ctx.deleteDatabase(name)
        val json = ctx.assets.open("app.cairn.core.database.CairnDatabase/1.json").bufferedReader().readText()
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        val entities = JSONObject(json).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        db.execSQL("INSERT INTO fitness (source, updatedAt, vo2max, runningLevel, thresholdPaceSecPerKm) VALUES ('COROS_MCP', 1000, 53, 73, 313)")
        db.execSQL("INSERT INTO activity (id, source, updatedAt, date, sport) VALUES ('L1', 'COROS_MCP', 1000, '2030-01-03', 'Run')")
        db.version = 1
        db.close()
    }

    @Test fun migrates1To2KeepingDataAndAddingTables() = runBlocking {
        createV1()
        val db = Room.databaseBuilder(ctx, CairnDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val f = db.fitness().observeAll().first().single().toDomain()
            assertEquals(53, f.vo2max); assertEquals(73.0, f.runningLevel!!, 0.0); assertEquals(313, f.thresholdPaceSecPerKm)
            assertEquals("L1", db.activities().observeSince("2000-01-01").first().single().id)
            // New tables exist and are empty; a decimal running level now round-trips.
            assertEquals(0, db.vitals().observeRange("2000-01-01", "2100-01-01").first().size)
            assertEquals(0, db.hrv().observeRange("2000-01-01", "2100-01-01").first().size)
            assertEquals(0, db.activityDetails().ids().size)
            db.fitness().upsert(f.copy(runningLevel = 73.3).toEntity())
            assertEquals(73.3, db.fitness().observeAll().first().single().runningLevel!!, 0.0001)
            assertNull(db.activityDetails().observeAll().first().firstOrNull())
        } finally { db.close(); ctx.deleteDatabase(name) }
    }
}
