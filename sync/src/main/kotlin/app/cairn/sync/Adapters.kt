package app.cairn.sync

import app.cairn.core.model.Activity
import app.cairn.core.model.DailySummary
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.TrainingLoadDay
import app.cairn.data.ActivityRepository
import app.cairn.data.DailyRepository
import app.cairn.data.FitnessRepository
import app.cairn.data.RecoveryRepository
import app.cairn.data.SleepRepository
import app.cairn.data.TrainingLoadRepository
import app.cairn.source.corosmcp.McpClient
import app.cairn.source.corosmcp.OAuthClient
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.parse.ToolReply
import app.cairn.source.healthconnect.HcAvailability
import app.cairn.source.healthconnect.HcInputs
import app.cairn.source.healthconnect.HealthConnectReader
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/** Production [CorosGateway]: one cached [McpClient] per region. */
class McpCorosGateway(
    private val http: OkHttpClient,
    private val oauth: OAuthClient,
    private val region: () -> Region,
) : CorosGateway {
    private var client: Pair<Region, McpClient>? = null

    @Synchronized private fun mcp(): McpClient {
        val r = region()
        client?.takeIf { it.first == r }?.let { return it.second }
        return McpClient(http, oauth, McpClient.endpointFor(r)).also { client = r to it }
    }

    override fun isSignedIn() = oauth.isSignedIn()
    override suspend fun listToolSchemas(): Map<String, JsonObject> = mcp().listToolSchemas()
    override suspend fun call(tool: String, args: JsonObject) = ToolReply.from(mcp().callTool(tool, args))
}

class HealthConnectSource(private val reader: HealthConnectReader) : HealthSource {
    override suspend fun canRead() = reader.availability() == HcAvailability.Available && reader.hasSyncPermission()
    override suspend fun read(from: Instant, to: Instant): HcInputs = reader.readInputs(from, to)
}

class RepositorySyncWriter(
    private val daily: DailyRepository,
    private val sleep: SleepRepository,
    private val recovery: RecoveryRepository,
    private val load: TrainingLoadRepository,
    private val fitness: FitnessRepository,
    private val activities: ActivityRepository,
) : SyncWriter {
    override suspend fun storeDaily(days: List<DailySummary>) = daily.store(days)
    override suspend fun storeSleep(nights: List<SleepNight>) = sleep.store(nights)
    override suspend fun storeRecovery(recovery: Recovery) = this.recovery.store(recovery)
    override suspend fun storeLoad(days: List<TrainingLoadDay>) = load.store(days)
    override suspend fun storeFitness(snapshot: FitnessSnapshot) = fitness.store(snapshot)
    override suspend fun storeActivities(activities: List<Activity>) = this.activities.store(activities)
}
