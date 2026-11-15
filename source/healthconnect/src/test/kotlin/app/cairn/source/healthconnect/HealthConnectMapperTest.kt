package app.cairn.source.healthconnect

import app.cairn.core.model.DataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectMapperTest {
    private val m = HealthConnectMapper(ZoneOffset.UTC)
    private fun t(s: String) = Instant.parse(s)
    private val d1 = LocalDate.of(2030, 1, 3)

    @Test fun dailySumsStepsAndTakesNewestModified() {
        val r = m.dailySummaries(
            steps = listOf(
                StepsInput(t("2030-01-03T08:00:00Z"), t("2030-01-03T09:00:00Z"), 1000, t("2030-01-03T09:05:00Z")),
                StepsInput(t("2030-01-03T10:00:00Z"), t("2030-01-03T11:00:00Z"), 2500, t("2030-01-03T11:10:00Z")),
                StepsInput(t("2030-01-04T01:00:00Z"), t("2030-01-04T02:00:00Z"), 7, t("2030-01-04T02:01:00Z")),
            ),
        )
        assertEquals(listOf(d1, LocalDate.of(2030, 1, 4)), r.map { it.date })
        val s = r[0].steps!!
        assertEquals(3500, s.value)
        assertEquals(DataSource.HEALTH_CONNECT, s.source)
        assertEquals(t("2030-01-03T11:10:00Z"), s.sourceUpdatedAt)
        assertNull(r[0].restingHr)
    }

    @Test fun dailyHeartRateAndResting() {
        val r = m.dailySummaries(
            heartRate = listOf(
                HeartRateInput(
                    listOf(HeartRateSampleInput(t("2030-01-03T08:00:00Z"), 60), HeartRateSampleInput(t("2030-01-03T08:01:00Z"), 91)),
                    t("2030-01-03T08:02:00Z"),
                ),
            ),
            restingHr = listOf(
                RestingHrInput(t("2030-01-03T03:00:00Z"), 50, t("2030-01-03T03:01:00Z")),
                RestingHrInput(t("2030-01-03T12:00:00Z"), 53, t("2030-01-03T12:01:00Z")),
            ),
            distance = listOf(DistanceInput(t("2030-01-03T08:00:00Z"), t("2030-01-03T09:00:00Z"), 4200.5, t("2030-01-03T09:00:00Z"))),
            calories = listOf(TotalCaloriesInput(t("2030-01-03T00:00:00Z"), t("2030-01-03T12:00:00Z"), 1234.6, t("2030-01-03T12:00:00Z"))),
        ).single()
        assertEquals(76, r.avgHr!!.value) // (60+91)/2 = 75.5 -> 76
        assertEquals(53, r.restingHr!!.value)
        assertEquals(4200.5, r.distanceMeters!!.value, 1e-9)
        assertEquals(1235, r.calories!!.value)
    }

    @Test fun emptyInputGivesEmpty() {
        assertTrue(m.dailySummaries().isEmpty())
        assertTrue(m.sleepNights(emptyList()).isEmpty())
        assertTrue(m.activities(emptyList()).isEmpty())
    }

    @Test fun sleepWithStages() {
        val n = m.sleepNights(
            listOf(
                SleepSessionInput(
                    t("2030-01-02T23:00:00Z"), t("2030-01-03T07:00:00Z"),
                    listOf(
                        SleepStageInput(t("2030-01-02T23:00:00Z"), t("2030-01-03T01:00:00Z"), SleepStageKind.LIGHT),
                        SleepStageInput(t("2030-01-03T01:00:00Z"), t("2030-01-03T02:00:00Z"), SleepStageKind.DEEP),
                        SleepStageInput(t("2030-01-03T02:00:00Z"), t("2030-01-03T02:30:00Z"), SleepStageKind.AWAKE),
                        SleepStageInput(t("2030-01-03T02:30:00Z"), t("2030-01-03T07:00:00Z"), SleepStageKind.REM),
                    ),
                    t("2030-01-03T07:05:00Z"),
                ),
            ),
        ).single()
        assertEquals(d1, n.date)
        assertEquals(480, n.inBedMinutes)
        assertEquals(450, n.asleepMinutes)
        assertEquals(60, n.deepMinutes); assertEquals(120, n.lightMinutes); assertEquals(270, n.remMinutes); assertEquals(30, n.awakeMinutes)
        assertNull(n.score)
        assertEquals(DataSource.HEALTH_CONNECT, n.stamp.source)
    }

    @Test fun sleepWithoutStagesAndNapsPickLongest() {
        val n = m.sleepNights(
            listOf(
                SleepSessionInput(t("2030-01-03T13:00:00Z"), t("2030-01-03T13:30:00Z"), emptyList(), t("2030-01-03T14:00:00Z")),
                SleepSessionInput(t("2030-01-02T23:00:00Z"), t("2030-01-03T06:00:00Z"), emptyList(), t("2030-01-03T06:10:00Z")),
            ),
        ).single()
        assertEquals(420, n.asleepMinutes)
        assertNull(n.deepMinutes)
        assertEquals(t("2030-01-03T14:00:00Z"), n.stamp.sourceUpdatedAt) // newest of the day's sessions
    }

    @Test fun activitiesJoinDistanceAndCalories() {
        val a = m.activities(
            listOf(
                ExerciseSessionInput("a", t("2030-01-03T07:00:00Z"), t("2030-01-03T07:30:00Z"), "Run", t("2030-01-03T07:31:00Z")),
                ExerciseSessionInput("b", t("2030-01-01T07:00:00Z"), t("2030-01-01T08:00:00Z"), "Walk", t("2030-01-01T08:01:00Z")),
            ),
            distance = listOf(DistanceInput(t("2030-01-03T07:00:00Z"), t("2030-01-03T07:30:00Z"), 5000.0, t("2030-01-03T07:31:00Z"))),
            calories = listOf(TotalCaloriesInput(t("2030-01-03T06:00:00Z"), t("2030-01-03T08:00:00Z"), 400.0, t("2030-01-03T08:00:00Z"))),
        )
        assertEquals(listOf("hc-a", "hc-b"), a.map { it.id })
        assertEquals(1800, a[0].durationSec)
        assertEquals(5000.0, a[0].distanceMeters!!, 1e-9)
        assertEquals(360, a[0].avgPaceSecPerKm)
        assertEquals(400, a[0].calories)
        assertNull(a[1].distanceMeters); assertNull(a[1].avgPaceSecPerKm)
    }
}
