package app.cairn.source.corosmcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerPolicyTest {
    private val callable = listOf(
        "queryActivityLapData", "queryCustomActivityLapData", "getActivityDetail", "queryDailyHealthData", "querySleepOverview",
        "queryDevices", "queryFitnessAssessmentOverview", "queryTrainingLoadAssessment", "queryRecoveryStatus", "querySportRecords",
        "queryAvgHeartRate", "queryRestingHeartRate", "querySleepHrv", "queryStressLevel", "queryHealthCheckTimeSeries",
        "queryStressTimeSeries", "queryScheduledWorkoutDetails", "queryTrainingPlanDetails", "queryTrainingPlanLibrary",
        "queryTrainingSchedule", "queryWorkoutDetails", "queryWorkoutLibrary", "queryUserInfo",
    )
    private val blocked = listOf(
        "queryMenstruationCycles", "uploadRouteFile", "createScheduledWorkout", "updateScheduledWorkout", "createTrainingPlan",
        "updateTrainingPlan", "createSingleWorkout", "scheduleWorkout", "updateWorkoutDetails", "downloadActivityFitFiles",
        "queryActivityFitFileDownloadUrls", "deleteWorkout", "", "somethingElse",
    )

    @Test fun allowedTools() = callable.forEach { assertTrue(it, ExplorerPolicy.isCallable(it)) }
    @Test fun blockedTools() = blocked.forEach { assertFalse(it, ExplorerPolicy.isCallable(it)); assertTrue(ExplorerPolicy.blockReason(it) != null) }

    @Test fun schemaSummary() {
        val s = Json.parseToJsonElement(
            """{"properties":{"startDate":{"type":"string","format":"date"},"sportType":{"type":"integer","enum":[1,2]},"n":{}},"required":["startDate"]}""",
        ).jsonObject
        assertEquals(
            listOf("startDate: string (required) format=date", "sportType: integer enum=[1,2]", "n: any"),
            SchemaSummary.summarize(s),
        )
        assertTrue(SchemaSummary.summarize(null).isEmpty())
    }

    @Test fun redactorExtensions() {
        val t = Redactor.redactText("Sport: Run | Location: Golden Gate Park, SF\nDevice Serial Number: ABC123XYZ9\nAthlete: Jane Doe contact jane@x.com")
        assertFalse(t.contains("Golden"))
        assertFalse(t.contains("ABC123"))
        assertFalse(t.contains("Jane"))
        assertFalse(t.contains("@"))
        assertTrue(t.contains("Sport: Run"))
    }
}
