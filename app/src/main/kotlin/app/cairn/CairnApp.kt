package app.cairn

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.work.WorkManager
import app.cairn.core.database.CairnDatabase
import app.cairn.core.datastore.EncryptedFileTokenStore
import app.cairn.core.datastore.KeystoreAesGcmCipher
import app.cairn.core.datastore.TokenStore
import app.cairn.data.ActivityDetailRepository
import app.cairn.data.ActivityRepository
import app.cairn.data.HrvRepository
import app.cairn.data.VitalsRepository
import app.cairn.data.DailyRepository
import app.cairn.data.FitnessRepository
import app.cairn.data.RecoveryRepository
import app.cairn.data.SleepRepository
import app.cairn.data.TrainingLoadRepository
import app.cairn.source.corosmcp.OAuthClient
import app.cairn.source.corosmcp.Region
import app.cairn.source.corosmcp.RegionPolicy
import app.cairn.source.healthconnect.HealthConnectReader
import app.cairn.sync.DataStoreNudgeSettings
import app.cairn.sync.StaleNudgeEvaluator
import app.cairn.sync.DataStoreSyncSettings
import app.cairn.sync.DataStoreSyncStateStore
import app.cairn.sync.HealthConnectSource
import app.cairn.sync.McpCorosGateway
import app.cairn.sync.RepositorySyncWriter
import app.cairn.sync.SyncComponent
import app.cairn.sync.SyncComponentProvider
import app.cairn.sync.SyncEngine
import app.cairn.sync.SyncScheduler
import app.cairn.sync.SyncState
import app.cairn.sync.WidgetUpdater
import app.cairn.widget.AuthState
import app.cairn.widget.GlanceWidgetUpdater
import app.cairn.widget.WidgetDataSources
import app.cairn.widget.WidgetEnvironment
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class CairnApp : Application(), SyncComponentProvider {
    val graph: AppGraph by lazy { AppGraph(this) }
    override val syncComponent: SyncComponent get() = graph

    override fun onCreate() {
        super.onCreate()
        app.cairn.appdrop.AppDropUpdater.init(this, "https://appdrop-store.vercel.app", "cairn")
        // Cairn draws its own update banner (ui/UpdateBanner.kt); no platform dialog.
        app.cairn.appdrop.AppDropUpdater.setDialogEnabled(false)
        app.cairn.appdrop.AppDropUpdater.refresh(this)
        DemoFeature.install(graph, this)
        graph.installWidgets()
        // Idempotent (UPDATE policy); also queues one APP_START refresh, which the MCP gap logic may skip.
        graph.appScope.launch { runCatching { graph.syncScheduler.onAppStart() } }
    }
}

/** Plain region cache (not secret) kept next to, not inside, the token blob. */
class RegionStore(private val file: File) {
    fun get(): Region? = runCatching { Region.valueOf(file.readText().trim()) }.getOrNull()
    fun set(r: Region) { runCatching { file.parentFile?.mkdirs(); file.writeText(r.name) } }
}

/**
 * Manual service locator: everything is created lazily, once. [widgetUpdater] refreshes the Glance widgets after each sync run.
 */
class AppGraph(private val context: Context) : SyncComponent {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // files/datastore/ is excluded from cloud backup and device transfer.
    private val datastoreDir = File(context.filesDir, "datastore")

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }
    val tokenStore: TokenStore by lazy { EncryptedFileTokenStore(File(datastoreDir, "coros_tokens.bin"), KeystoreAesGcmCipher()) }
    val oauth: OAuthClient by lazy { OAuthClient(http, tokenStore) }
    val regionStore: RegionStore by lazy { RegionStore(File(datastoreDir, "coros_region.txt")) }
    val pendingStore: app.cairn.source.corosmcp.PendingStore by lazy {
        app.cairn.source.corosmcp.EncryptedFilePendingStore(File(datastoreDir, "coros_pending.bin"), KeystoreAesGcmCipher())
    }
    val signIn: SignInCoordinator by lazy {
        SignInCoordinator(context, oauth, tokenStore, pendingStore, regionStore, appScope, File(datastoreDir, "signin_diag.json"), onSignedIn = ::onSignedIn)
    }

    val database: CairnDatabase by lazy { Room.databaseBuilder(context, CairnDatabase::class.java, CairnDatabase.NAME).addMigrations(*app.cairn.core.database.ALL_MIGRATIONS).build() }
    val dailyRepository by lazy { DailyRepository(database.daily()) }
    val sleepRepository by lazy { SleepRepository(database.sleep()) }
    val recoveryRepository by lazy { RecoveryRepository(database.recovery()) }
    val trainingLoadRepository by lazy { TrainingLoadRepository(database.trainingLoad()) }
    val fitnessRepository by lazy { FitnessRepository(database.fitness()) }
    val activityRepository by lazy { ActivityRepository(database.activities()) }
    val vitalsRepository by lazy { VitalsRepository(database.vitals()) }
    val hrvRepository by lazy { HrvRepository(database.hrv()) }
    val activityDetailRepository by lazy { ActivityDetailRepository(database.activityDetails()) }

    val healthConnect: HealthConnectReader by lazy { HealthConnectReader(context) }

    val prefs by lazy {
        PreferenceDataStoreFactory.create(scope = appScope, produceFile = { File(datastoreDir.apply { mkdirs() }, "sync.preferences_pb") })
    }
    override val syncSettings by lazy { DataStoreSyncSettings(prefs) }
    override val syncState by lazy { DataStoreSyncStateStore(prefs) }
    val nudgeSettings by lazy { DataStoreNudgeSettings(prefs) }
    val appSettings by lazy { AppSettings(prefs) }
    val notifier by lazy { StaleNudgeNotifier(context) }

    @Volatile var widgetUpdater: WidgetUpdater = GlanceWidgetUpdater(context)

    override val syncEngine: SyncEngine by lazy {
        SyncEngine(
            hc = HealthConnectSource(healthConnect),
            coros = McpCorosGateway(http, oauth) { RegionPolicy.start(regionStore.get()) },
            writer = RepositorySyncWriter(dailyRepository, sleepRepository, recoveryRepository, trainingLoadRepository, fitnessRepository, activityRepository, vitalsRepository, hrvRepository, activityDetailRepository),
            store = syncState,
            widgets = object : WidgetUpdater { override suspend fun updateAll() = widgetUpdater.updateAll() },
            nudge = StaleNudgeEvaluator(nudgeSettings, syncState, notifier),
        )
    }
    override val syncScheduler: SyncScheduler by lazy { SyncScheduler(WorkManager.getInstance(context), syncSettings, syncState) }

    /** Widgets render from Room only; give them the repositories and the sign-in state. */
    fun installWidgets() {
        app.cairn.widget.WidgetLog.enabled = BuildConfig.DEBUG
        WidgetEnvironment.install(
            WidgetDataSources(dailyRepository, sleepRepository, recoveryRepository, activityRepository, trainingLoadRepository, fitnessRepository, vitals = vitalsRepository, hrv = hrvRepository, details = activityDetailRepository, prefs = { appSettings.displayNow() }, settings = appSettings.widgetSettings, auth = { authState() }, syncState = syncState.state),
        )
        WidgetEnvironment.requestSync = { syncScheduler.requestUserSync() }
        runCatching { KeepAliveWorker.schedule(context) }
        // Android 15+: show the coloured, synthetic-data previews in the launcher's widget picker.
        appScope.launch { runCatching { app.cairn.widget.registerWidgetPreviews(context) } }
    }

    suspend fun authState(): AuthState = when {
        DemoFeature.active -> AuthState.Ok
        syncState.get().needsSignIn -> AuthState.NeedsSignIn
        tokenStore.load() == null -> AuthState.SignedOut
        else -> AuthState.Ok
    }

    /** "Delete all Cairn data": every cached health row, the sync state and the reminder marker. Sign-in is untouched (use Sign out). */
    suspend fun deleteAllData() {
        dailyRepository.clear(); sleepRepository.clear(); recoveryRepository.clear()
        trainingLoadRepository.clear(); fitnessRepository.clear(); activityRepository.clear()
        vitalsRepository.clear(); hrvRepository.clear(); activityDetailRepository.clear()
        syncState.update { SyncState() }
        nudgeSettings.setLastNotifiedDay(null)
        notifier.cancel()
        runCatching { widgetUpdater.updateAll() }
    }

    fun refreshWidgets() { appScope.launch { runCatching { widgetUpdater.updateAll() } } }

    fun onSignedIn() { appScope.launch { runCatching { syncScheduler.onSignedIn() } } }
    fun onSignedOut() { appScope.launch { runCatching { syncScheduler.onSignedOut() } } }
    fun onPermissionChanged() { appScope.launch { runCatching { syncScheduler.onPermissionChanged() } } }
}
