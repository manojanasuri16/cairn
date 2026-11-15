package app.cairn.sync

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.TrainingLoadDay
import app.cairn.data.Clock
import app.cairn.source.corosmcp.parse.ToolReply
import app.cairn.source.healthconnect.HcInputs
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

class FakeClock(var now: Instant = Instant.parse("2030-01-04T10:00:00Z")) : Clock {
    override fun now(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

const val DAILY_TEXT = """Daily Health Data — Last 3 days | Resting HR: 55 bpm
--- 20300103 ---
Steps: 12,050 | Calories: 880 kcal | Exercise: 64 min
Sleep Summary:
 Total: 6h 5min | Deep: 55 min | Light: 3h 20min | REM: 1h 30min | Awake: 20 min
"""
const val RECOVERY_TEXT = "Recovery Status\n===\nRecovery: 64%\nLevel: Moderate\nEstimated Full Recovery: 18h\n"
const val SLEEP_TEXT = "Sleep Overview\n===\n\n2030-01-04\nSleep Score: 78\nMain Sleep (asleep): 6h 50min\nMain Sleep Period (incl. awake): 7h 20min\n"
const val LOAD_TEXT = "Training Load Assessment\n===\n\n2030-01-03\nComment: Optimal\nShort-Term Load: 55\nLong-Term Load: 48\nLoad Ratio: 1.15\n"
const val FITNESS_TEXT = "Fitness Assessment Overview\n===\n\nVO2max: 47\nRunning Level: 61\n"
const val SPORT_TEXT = "Sport Records — 2030-01-01 to 2030-01-03 (1 records)\n====\n\n1. Outdoor Run — 2030-01-03\n Time Window: startTimestamp=1893571200 | endTimestamp=1893574800\n Duration: 54:37 | Distance: 5.55 km\n"

class FakeCoros : CorosGateway {
    var signedIn = true
    var schemas: Map<String, JsonObject> = emptyMap()
    val replies = mutableMapOf(
        McpTools.DAILY to DAILY_TEXT, McpTools.RECOVERY to RECOVERY_TEXT, McpTools.SLEEP to SLEEP_TEXT,
        McpTools.LOAD to LOAD_TEXT, McpTools.FITNESS to FITNESS_TEXT, McpTools.SPORT to SPORT_TEXT,
    )
    val failures = mutableMapOf<String, Exception>()
    var listFailure: Exception? = null
    val calls = mutableListOf<Pair<String, JsonObject>>()
    var listCalls = 0
    val toolNames get() = calls.map { it.first }

    override fun isSignedIn() = signedIn
    override suspend fun listToolSchemas(): Map<String, JsonObject> { listCalls++; listFailure?.let { throw it }; return schemas }
    override suspend fun call(tool: String, args: JsonObject): ToolReply {
        calls += tool to args
        failures[tool]?.let { throw it }
        return ToolReply(replies.getValue(tool))
    }
    fun reset() { calls.clear(); listCalls = 0 }
}

class FakeHc : HealthSource {
    var can = true
    var inputs = HcInputs()
    var failure: Exception? = null
    var reads = 0
    override suspend fun canRead() = can
    override suspend fun read(from: Instant, to: Instant): HcInputs { reads++; failure?.let { throw it }; return inputs }
}

class FakeWriter : SyncWriter {
    val daily = mutableListOf<DailySummary>()
    val sleep = mutableListOf<SleepNight>()
    val recovery = mutableListOf<Recovery>()
    val load = mutableListOf<TrainingLoadDay>()
    val fitness = mutableListOf<FitnessSnapshot>()
    val activities = mutableListOf<Activity>()
    override suspend fun storeDaily(days: List<DailySummary>) { daily += days }
    override suspend fun storeSleep(nights: List<SleepNight>) { sleep += nights }
    override suspend fun storeRecovery(recovery: Recovery) { this.recovery += recovery }
    override suspend fun storeLoad(days: List<TrainingLoadDay>) { load += days }
    override suspend fun storeFitness(snapshot: FitnessSnapshot) { fitness += snapshot }
    override suspend fun storeActivities(activities: List<Activity>) { this.activities += activities }
}

class InMemoryStateStore(initial: SyncState = SyncState()) : SyncStateStore {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<SyncState> = flow
    override suspend fun get() = flow.value
    override suspend fun update(transform: (SyncState) -> SyncState): SyncState { flow.value = transform(flow.value); return flow.value }
}

class InMemorySettings(var value: Int = SyncSettings.DEFAULT) : SyncSettings {
    private val flow = MutableStateFlow(value)
    override val intervalMinutes: Flow<Int> = flow
    override suspend fun interval() = flow.value
    override suspend fun setInterval(minutes: Int) { flow.value = SyncSettings.normalize(minutes) }
}

class CountingWidgets : WidgetUpdater {
    var count = 0
    override suspend fun updateAll() { count++ }
}
