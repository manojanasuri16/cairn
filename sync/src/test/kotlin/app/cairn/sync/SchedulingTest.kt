package app.cairn.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.Configuration
import app.cairn.source.corosmcp.CorosNetworkException
import java.time.Duration
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class TestApp : Application(), SyncComponentProvider {
    val coros = FakeCoros()
    val store = InMemoryStateStore()
    val settings = InMemorySettings()
    lateinit var scheduler: SyncScheduler
    override val syncComponent: SyncComponent by lazy {
        val engine = SyncEngine(FakeHc(), coros, FakeWriter(), store, NoOpWidgetUpdater, FakeClock(), ZoneOffset.UTC)
        object : SyncComponent {
            override val syncEngine = engine
            override val syncState = store
            override val syncSettings = settings
            override val syncScheduler get() = scheduler
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [35])
class SchedulingTest {
    private lateinit var app: TestApp
    private lateinit var wm: WorkManager

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext<Context>() as TestApp
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        wm = WorkManager.getInstance(app)
        app.scheduler = SyncScheduler(wm, app.settings, app.store)
    }

    @Test fun periodicRequestUsesIntervalAndNetworkOnlyConstraint() {
        val req = SyncScheduler.periodicRequest(60)
        assertEquals(TimeUnit.MINUTES.toMillis(60), req.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, req.workSpec.constraints.requiredNetworkType)
        assertTrue(!req.workSpec.constraints.requiresCharging() && !req.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.MINUTES.toMillis(30), SyncScheduler.periodicRequest(7).workSpec.intervalDuration) // invalid -> default
    }

    @Test fun fiveMinuteModeUsesSelfReschedulingChain() = runTest {
        app.scheduler.setInterval(5)
        assertEquals(0, wm.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_NAME).get().filter { !it.state.isFinished }.size)
        val chain = wm.getWorkInfosForUniqueWork(SyncScheduler.FAST_NAME).get()
        assertEquals(1, chain.size)
        assertEquals(TimeUnit.MINUTES.toMillis(5), SyncScheduler.fastRequest().workSpec.initialDelay)
        app.scheduler.setInterval(30)
        assertTrue(wm.getWorkInfosForUniqueWork(SyncScheduler.FAST_NAME).get().all { it.state.isFinished })
        assertEquals(1, wm.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_NAME).get().size)
    }

    @Test fun userSyncMarksSyncingAndQueuesExpeditedRun() = runTest {
        app.scheduler.requestUserSync(1234L)
        assertEquals(1234L, app.store.get().userSyncRequestedMs)
        assertEquals(1, wm.getWorkInfosForUniqueWork(SyncScheduler.ONE_TIME_NAME).get().size)
    }

    @Test fun oneTimeRequestIsExpeditedWithFallback() {
        val req = SyncScheduler.oneTimeRequest(SyncReason.MANUAL)
        assertTrue(req.workSpec.expedited)
        assertEquals("MANUAL", req.workSpec.input.getString(SyncScheduler.KEY_REASON))
    }

    @Test fun ensurePeriodicKeepsOneUniqueJobAndUpdatesInterval() = runTest {
        app.scheduler.ensurePeriodic()
        app.scheduler.setInterval(15)
        app.scheduler.ensurePeriodic()
        val infos = wm.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertEquals(15, app.settings.interval())
    }

    @Test fun onSignedInClearsNeedsSignInAndQueuesOneTimeSync() = runTest {
        app.store.update { it.copy(needsSignIn = true) }
        app.scheduler.onSignedIn()
        assertEquals(false, app.store.get().needsSignIn)
        assertEquals(1, wm.getWorkInfosForUniqueWork(SyncScheduler.ONE_TIME_NAME).get().size)
        assertEquals(1, wm.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_NAME).get().size)
    }

    @Test fun workerRunsEngineAndReturnsSuccessAndNeverThrows() {
        app.coros.signedIn = false
        val w = TestListenableWorkerBuilder<PeriodicSyncWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { w.doWork() })
        assertEquals(1, runBlocking { app.store.get().runs.size })
    }

    @Test fun networkFailureRetriesButOnlyUpToTheAttemptCap() {
        app.coros.failures[McpTools.DAILY] = CorosNetworkException("Network error: X")
        val first = TestListenableWorkerBuilder<OneTimeSyncWorker>(app).setRunAttemptCount(0).build()
        assertEquals(ListenableWorker.Result.retry(), runBlocking { first.doWork() })
        val last = TestListenableWorkerBuilder<OneTimeSyncWorker>(app).setRunAttemptCount(BaseSyncWorker.MAX_ATTEMPTS).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { last.doWork() })
    }

    @Test fun stopReasonNamesCoverKnownCodes() {
        assertEquals("QUOTA", StopReasons.name(WorkInfo.STOP_REASON_QUOTA))
        assertEquals("CODE_999", StopReasons.name(999))
    }
}
