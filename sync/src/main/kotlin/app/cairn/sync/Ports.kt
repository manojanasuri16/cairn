package app.cairn.sync

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.TrainingLoadDay
import app.cairn.source.corosmcp.parse.ToolReply
import app.cairn.source.healthconnect.HcInputs
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** COROS MCP seen by the engine. Throws CorosAuthException / CorosNetworkException / CorosException. */
interface CorosGateway {
    fun isSignedIn(): Boolean
    /** `tools/list` as `name -> inputSchema`. */
    suspend fun listToolSchemas(): Map<String, JsonObject>
    suspend fun call(tool: String, args: JsonObject): ToolReply
}

interface HealthSource {
    /** Health Connect available and at least one sync permission granted. */
    suspend fun canRead(): Boolean
    suspend fun read(from: Instant, to: Instant): HcInputs
}

/** Where parsed data is written (Room repositories in production). */
interface SyncWriter {
    suspend fun storeDaily(days: List<DailySummary>)
    suspend fun storeSleep(nights: List<SleepNight>)
    suspend fun storeRecovery(recovery: Recovery)
    suspend fun storeLoad(days: List<TrainingLoadDay>)
    suspend fun storeFitness(snapshot: FitnessSnapshot)
    suspend fun storeActivities(activities: List<Activity>)
}

/** Implemented by the widget module in M5; [updateAll] re-renders every widget from Room. */
interface WidgetUpdater { suspend fun updateAll() }

object NoOpWidgetUpdater : WidgetUpdater { override suspend fun updateAll() = Unit }

interface SyncStateStore {
    val state: Flow<SyncState>
    suspend fun get(): SyncState
    /** Atomic read-modify-write. */
    suspend fun update(transform: (SyncState) -> SyncState): SyncState
}

interface SyncSettings {
    val intervalMinutes: Flow<Int>
    suspend fun interval(): Int
    suspend fun setInterval(minutes: Int)

    companion object {
        /** 5 minutes is a self-rescheduling chain of one-time work (WorkManager's periodic floor is 15). */
        val CHOICES = listOf(5, 15, 30, 60)
        const val FAST = 5
        const val DEFAULT = 30
        fun normalize(m: Int?): Int = if (m in CHOICES) m!! else DEFAULT
    }
}
