package app.cairn.source.corosmcp.parse

import app.cairn.core.model.DataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private val now = Instant.parse("2030-01-04T08:00:00Z")
    private fun fx(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    // ---- daily ----
    @Test fun dailyFullFixture() {
        val r = DailyHealthParser.parse(fx("daily_health.txt"), now)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        val d = r.value
        assertEquals(3, d.days.size)
        assertEquals(61, d.hrvBaselineMs)
        val d1 = d.days[0]
        assertEquals(LocalDate.of(2030, 1, 2), d1.date)
        assertEquals(4321, d1.steps!!.value)
        assertEquals(310, d1.calories!!.value)
        assertEquals(12, d1.exerciseMinutes!!.value)
        assertEquals(31, d1.avgStress!!.value)
        assertNull(d1.floors)
        assertNull(d1.restingHr)
        assertEquals(DataSource.COROS_MCP, d1.steps!!.source)
        assertEquals(now, d1.steps!!.sourceUpdatedAt)
        assertEquals(7, d.days[1].floors!!.value)
        assertEquals(12050, d.days[1].steps!!.value)
        // resting HR from the header lands on the newest day only
        assertEquals(55, d.days[2].restingHr!!.value)
        assertEquals(987, d.days[2].steps!!.value)
    }

    @Test fun dailySleepSummary() {
        val s = DailyHealthParser.parse(fx("daily_health.txt"), now).value.sleep
        assertEquals(2, s.size) // third day has no sleep block
        val n = s[0]
        assertEquals(LocalDate.of(2030, 1, 2), n.date)
        assertEquals(450, n.inBedMinutes)
        assertEquals(415, n.asleepMinutes)
        assertEquals(70, n.deepMinutes)
        assertEquals(240, n.lightMinutes)
        assertEquals(105, n.remMinutes)
        assertEquals(35, n.awakeMinutes)
        assertEquals(57, n.avgHr); assertEquals(49, n.minHr); assertEquals(80, n.maxHr)
        assertNull(n.score)
    }

    @Test fun dailySparseGivesPartialResultsAndWarnings() {
        val r = DailyHealthParser.parse(fx("daily_health_sparse.txt"), now)
        assertEquals(1, r.value.days.size)
        assertEquals(1000, r.value.days[0].steps!!.value)
        assertNull(r.value.days[0].calories)
        assertNull(r.value.days[0].avgStress)
        assertNull(r.value.hrvBaselineMs)
        assertEquals(480, r.value.sleep.single().inBedMinutes)
        assertTrue(r.warnings.any { "calories" in it })
        assertTrue(r.warnings.any { "unrecognized day header" in it })
    }

    // ---- sleep overview ----
    @Test fun sleepOverview() {
        val r = SleepOverviewParser.parse(fx("sleep_overview.txt"), now, ZoneOffset.UTC)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        assertEquals(2, r.value.size)
        val a = r.value[0]
        assertEquals(LocalDate.of(2030, 1, 2), a.date)
        assertEquals(78, a.score)
        assertEquals(410, a.asleepMinutes) // main sleep (asleep) preferred over daily incl. naps
        assertEquals(440, a.inBedMinutes)
        assertEquals(30, a.awakeMinutes)
        assertEquals(66, a.deepMinutes) // 15% of 440
        assertEquals(Instant.parse("2030-01-01T23:30:00Z"), a.start)
        assertEquals(Instant.parse("2030-01-02T06:50:00Z"), a.end)
        assertEquals(91, r.value[1].score)
    }

    @Test fun sleepOverviewTimezoneApplied() {
        val r = SleepOverviewParser.parse(fx("sleep_overview.txt"), now, ZoneOffset.ofHours(2))
        assertEquals(Instant.parse("2030-01-01T21:30:00Z"), r.value[0].start)
    }

    @Test fun sleepOverviewSparse() {
        val r = SleepOverviewParser.parse(fx("sleep_overview_sparse.txt"), now, ZoneOffset.UTC)
        assertEquals(1, r.value.size)
        assertNull(r.value[0].score)
        assertEquals(305, r.value[0].asleepMinutes)
        assertNull(r.value[0].deepMinutes)
        assertTrue(r.warnings.any { "sleep score" in it })
        assertTrue(r.warnings.any { "no fields" in it })
    }

    // ---- real servers wrap each report in a JSON string literal (one line, escaped \n) ----
    @Test fun quotedJsonStringReplyIsUnwrapped() {
        val raw = """{"content":[{"type":"text","text":"\"Recovery Status\\n========================\\n\\nRecovery: 64%\\nLevel: Moderate\\nEstimated Full Recovery: 18h\""}],"isError":false}"""
        val reply = ToolReply.from(Json.parseToJsonElement(raw) as JsonObject)
        assertTrue(reply.text.contains("\n"))
        val v = RecoveryParser.parse(reply, now).value!!
        assertEquals(64, v.percent); assertEquals("Moderate", v.level); assertEquals(18, v.hoursToFullRecovery)
    }

    @Test fun plainTextReplyIsLeftAlone() {
        assertEquals("Recovery: 64%", ToolReply.unwrapQuoted("Recovery: 64%"))
        assertEquals("\"not closed", ToolReply.unwrapQuoted("\"not closed"))
    }

    // ---- recovery ----
    @Test fun recovery() {
        val r = RecoveryParser.parse(fx("recovery.txt"), now)
        assertTrue(r.warnings.isEmpty())
        val v = r.value!!
        assertEquals(64, v.percent); assertEquals("Moderate", v.level); assertEquals(18, v.hoursToFullRecovery)
        assertEquals(now, v.stamp.sourceUpdatedAt)
    }

    @Test fun recoveryMissingPercent() {
        val r = RecoveryParser.parse(fx("recovery_missing.txt"), now)
        assertNull(r.value!!.percent)
        assertEquals("Fresh", r.value!!.level)
        assertTrue(r.warnings.any { "percent missing" in it })
    }

    // ---- training load ----
    @Test fun trainingLoad() {
        val r = TrainingLoadParser.parse(fx("training_load.txt"), now)
        assertTrue(r.warnings.isEmpty())
        assertEquals(2, r.value.size)
        val a = r.value[0]
        assertEquals(LocalDate.of(2030, 1, 3), a.date)
        assertEquals(55, a.shortTermLoad); assertEquals(48, a.longTermLoad)
        assertEquals(1.15, a.loadRatio!!, 1e-9); assertEquals("Optimal", a.comment)
    }

    @Test fun trainingLoadSparse() {
        val r = TrainingLoadParser.parse(fx("training_load_sparse.txt"), now)
        assertEquals(1, r.value.size)
        assertEquals(12, r.value[0].shortTermLoad)
        assertNull(r.value[0].longTermLoad)
        assertTrue(r.warnings.any { "load ratio" in it })
    }

    // ---- fitness ----
    @Test fun fitness() {
        val r = FitnessParser.parse(fx("fitness.txt"), now)
        assertTrue(r.warnings.isEmpty())
        val f = r.value!!
        assertEquals(47, f.vo2max); assertEquals(61, f.runningLevel)
        assertEquals(285, f.thresholdPaceSecPerKm)
        assertEquals(23 * 60 + 40, f.prediction5kSec)
        assertEquals(49 * 60 + 10, f.prediction10kSec)
        assertEquals(3600 + 48 * 60 + 30, f.predictionHalfSec)
        assertEquals(3 * 3600 + 45 * 60 + 5, f.predictionMarathonSec)
    }

    @Test fun fitnessUnitsAndMissing() {
        val r = FitnessParser.parse(fx("fitness_sparse.txt"), now)
        val f = r.value!!
        assertEquals(44, f.vo2max)
        assertEquals(280, f.thresholdPaceSecPerKm) // 7:30 /mi -> 279.6 s/km
        assertNull(f.prediction5kSec)
        assertNull(f.runningLevel)
        assertTrue(r.warnings.any { "5 km prediction" in it })
    }

    // ---- sport records ----
    @Test fun sportRecords() {
        val r = SportRecordsParser.parse(fx("sport_records.txt"), now)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        assertEquals(3, r.value.size)
        val a = r.value[0]
        assertEquals("L1001", a.id); assertEquals("Outdoor Run", a.sport)
        assertEquals(LocalDate.of(2030, 1, 3), a.date)
        assertEquals(54 * 60 + 37, a.durationSec)
        assertEquals(5550.0, a.distanceMeters!!, 1e-6)
        assertEquals(590, a.avgPaceSecPerKm); assertEquals(151, a.avgHr); assertEquals(703, a.calories)
        assertEquals("Testville Park", a.location); assertEquals(100, a.sportType)
        assertEquals(Instant.ofEpochSecond(1893571200), a.start)
    }

    @Test fun sportRecordsUnitVariationsAndExtraLines() {
        val r = SportRecordsParser.parse(fx("sport_records.txt"), now).value
        assertEquals(3910, r[1].durationSec) // 1:05:10
        assertEquals(800.0, r[1].distanceMeters!!, 1e-6)
        assertNull(r[1].location)
        assertEquals(1.5 * 1609.344, r[2].distanceMeters!!, 1e-6)
        assertNull(r[2].avgHr); assertNull(r[2].start)
        assertEquals("L1003", r[2].id)
    }

    @Test fun sportRecordsSparseAndCountMismatch() {
        val r = SportRecordsParser.parse(fx("sport_records_sparse.txt"), now)
        assertEquals(1, r.value.size)
        assertNull(r.value[0].durationSec)
        assertEquals("2030-01-03-Trail Run-1", r.value[0].id) // synthesized id
        assertTrue(r.warnings.any { "duration" in it })
        assertTrue(r.warnings.any { "header says 2" in it })
    }

    // ---- empty / error replies ----
    @Test fun emptyAndNoDataReplies() {
        val empty = ""
        val nodata = fx("no_data.txt")
        assertTrue(DailyHealthParser.parse(empty, now).value.days.isEmpty())
        assertTrue(DailyHealthParser.parse(nodata, now).warnings.single().startsWith("no data"))
        assertTrue(SleepOverviewParser.parse(nodata, now).value.isEmpty())
        assertNull(RecoveryParser.parse(nodata, now).value)
        assertTrue(TrainingLoadParser.parse(empty, now).warnings.single() == "empty reply")
        assertNull(FitnessParser.parse(nodata, now).value)
        assertTrue(SportRecordsParser.parse(nodata, now).value.isEmpty())
    }

    @Test fun garbageNeverThrows() {
        val junk = "???\n:::\n| | |\n--- \n1. — \n\u0000"
        assertFalse(DailyHealthParser.parse(junk, now).warnings.isEmpty())
        assertTrue(SleepOverviewParser.parse(junk, now).value.isEmpty())
        assertNull(RecoveryParser.parse(junk, now).value)
        assertTrue(TrainingLoadParser.parse(junk, now).value.isEmpty())
        assertNull(FitnessParser.parse(junk, now).value)
        assertTrue(SportRecordsParser.parse(junk, now).value.isEmpty())
    }

    @Test fun isErrorReplyYieldsEmptyWithWarning() {
        val reply = ToolReply.from(Json.parseToJsonElement(fx("error_reply.json")) as JsonObject)
        assertTrue(reply.isError)
        val r = RecoveryParser.parse(reply, now)
        assertNull(r.value)
        assertTrue(r.warnings.single().startsWith("tool returned an error"))
        assertTrue(DailyHealthParser.parse(reply, now).value.days.isEmpty())
        assertTrue(SportRecordsParser.parse(reply, now).value.isEmpty())
    }

    @Test fun replyTextPartsAreJoined() {
        val reply = ToolReply.from(Json.parseToJsonElement(fx("ok_reply.json")) as JsonObject)
        assertFalse(reply.isError)
        val r = RecoveryParser.parse(reply, now).value
        assertNotNull(r)
        assertEquals(70, r!!.percent); assertEquals(6, r.hoursToFullRecovery)
    }

    // ---- helpers ----
    @Test fun txtHelpers() {
        assertEquals(493, Txt.minutes("8h 13min"))
        assertEquals(53, Txt.minutes("53 min"))
        assertEquals(720, Txt.minutes("12h"))
        assertNull(Txt.minutes("soon"))
        assertEquals(7087, Txt.clockSeconds("1:58:07"))
        assertEquals(1506, Txt.clockSeconds("25:06"))
        assertEquals(3271, Txt.int("3,271"))
        assertEquals(1000.0, Txt.distanceMeters("1 km")!!, 1e-9)
        assertNull(Txt.distanceMeters("5"))
    }
}
