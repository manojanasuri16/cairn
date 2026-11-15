package app.cairn.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Implemented by the Application so workers (created by WorkManager's default factory) can reach the graph. */
interface SyncComponentProvider { val syncComponent: SyncComponent }

interface SyncComponent {
    val syncEngine: SyncEngine
    val syncState: SyncStateStore
    val syncSettings: SyncSettings
    val syncScheduler: SyncScheduler
}

/** Pure scheduling decision for an interval choice. */
sealed interface SyncSchedule {
    data class Periodic(val minutes: Int) : SyncSchedule
    /** Self-rescheduling one-time chain (best effort under Doze). */
    data class FastChain(val minutes: Int) : SyncSchedule

    companion object {
        const val FAST_DELAY_MIN = 5L
        fun decide(intervalMinutes: Int): SyncSchedule {
            val m = SyncSettings.normalize(intervalMinutes)
            return if (m == SyncSettings.FAST) FastChain(m) else Periodic(m)
        }
    }
}

class SyncScheduler(private val work: WorkManager, private val settings: SyncSettings, private val state: SyncStateStore) {
    companion object {
        const val PERIODIC_NAME = "cairn-sync-periodic"
        const val ONE_TIME_NAME = "cairn-sync-now"
        const val FAST_NAME = "cairn-sync-fast"
        const val KEY_REASON = "reason"
        private val CONSTRAINTS = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun periodicRequest(intervalMinutes: Int): PeriodicWorkRequest =
            PeriodicWorkRequest.Builder(PeriodicSyncWorker::class.java, SyncSettings.normalize(intervalMinutes).toLong(), TimeUnit.MINUTES)
                .setConstraints(CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

        fun fastRequest(): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(FastSyncWorker::class.java)
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(SyncSchedule.FAST_DELAY_MIN, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

        fun corosRefreshRequest(delaySeconds: Long): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OneTimeSyncWorker::class.java)
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(Data.Builder().putString(KEY_REASON, SyncReason.COROS_REFRESH.name).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

        /** Expedited when quota allows, otherwise a normal one-time job. */
        fun oneTimeRequest(reason: SyncReason): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OneTimeSyncWorker::class.java)
                .setConstraints(CONSTRAINTS)
                .setInputData(Data.Builder().putString(KEY_REASON, reason.name).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
    }

    /** UPDATE keeps one unique periodic job and applies a changed interval. */
    suspend fun ensurePeriodic() {
        when (val d = SyncSchedule.decide(settings.interval())) {
            is SyncSchedule.Periodic -> {
                work.cancelUniqueWork(FAST_NAME)
                work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodicRequest(d.minutes))
            }
            is SyncSchedule.FastChain -> {
                work.cancelUniqueWork(PERIODIC_NAME)
                // KEEP: re-ensuring (app start) does not reset a countdown that is already running.
                work.enqueueUniqueWork(FAST_NAME, ExistingWorkPolicy.KEEP, fastRequest())
            }
        }
    }

    /** Queues the next link of the 5 minute chain; REPLACE so a stale link never lingers. Called by [FastSyncWorker]. */
    fun scheduleNextFast() {
        work.enqueueUniqueWork(FAST_NAME, ExistingWorkPolicy.REPLACE, fastRequest())
    }

    /** KEEP: a tap while a run is queued or running does not stack another. */
    fun requestNow(reason: SyncReason = SyncReason.MANUAL) {
        work.enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, oneTimeRequest(reason))
    }

    /** A person asked: marks "syncing" (widgets and headers show it) and queues an immediate expedited run. */
    suspend fun requestUserSync(now: Long = System.currentTimeMillis()) {
        state.update { it.copy(userSyncRequestedMs = now) }
        requestNow(SyncReason.USER)
    }

    /** Two delayed refreshes after COROS was opened; REPLACE so re-opening restarts the countdown. */
    fun scheduleCorosRefreshes() {
        CorosRefreshPlanner.delaysSeconds().forEachIndexed { i, d ->
            work.enqueueUniqueWork(CorosRefreshPlanner.workName(i), ExistingWorkPolicy.REPLACE, corosRefreshRequest(d))
        }
    }

    suspend fun setInterval(minutes: Int) { settings.setInterval(minutes); ensurePeriodic() }

    suspend fun onAppStart() { ensurePeriodic(); requestNow(SyncReason.APP_START) }

    suspend fun onSignedIn() {
        state.update { it.copy(needsSignIn = false, lastErrorType = null) }
        ensurePeriodic(); requestNow(SyncReason.SIGN_IN)
    }

    suspend fun onSignedOut() { state.update { it.copy(needsSignIn = false, lastErrorType = null) } }

    suspend fun onPermissionChanged() { ensurePeriodic(); requestNow(SyncReason.PERMISSION) }
}

abstract class BaseSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    protected abstract fun reason(): SyncReason

    private fun component() = (applicationContext as? SyncComponentProvider)?.syncComponent

    override suspend fun doWork(): Result {
        val c = component() ?: return Result.success()
        return try {
            val r = c.syncEngine.syncOnce(reason())
            // Retry (WorkManager exponential backoff) only for network failures, and only a few times.
            if (r.retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            recordStop(c)
            throw e
        } catch (e: Throwable) {
            Result.success() // never fail the worker chain
        }
    }

    /** Records why the system stopped us (API 31+; minSdk is 34). CoroutineWorker.onStopped is final, so this runs on cancellation. */
    private suspend fun recordStop(c: SyncComponent) {
        val code = stopReason
        withContext(NonCancellable) {
            runCatching {
                c.syncState.update { s ->
                    s.copy(stops = (listOf(StopRecord(System.currentTimeMillis(), code, StopReasons.name(code))) + s.stops).take(SyncEngine.MAX_STOPS))
                }
            }
        }
    }

    companion object { const val MAX_ATTEMPTS = 3 }
}

class PeriodicSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {
    override fun reason() = SyncReason.PERIODIC
}

/** One link of the 5 minute chain: a light run, then queue the next while the 5 minute choice is still on. */
class FastSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {
    override fun reason() = SyncReason.FAST_PERIODIC
    override suspend fun doWork(): Result {
        val result = super.doWork()
        if (result == Result.retry()) return result
        val c = (applicationContext as? SyncComponentProvider)?.syncComponent
        if (c != null && SyncSchedule.decide(c.syncSettings.interval()) is SyncSchedule.FastChain) c.syncScheduler.scheduleNextFast()
        return result
    }
}

class OneTimeSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {
    override fun reason() = runCatching { SyncReason.valueOf(inputData.getString(SyncScheduler.KEY_REASON).orEmpty()) }.getOrDefault(SyncReason.MANUAL)
}

object StopReasons {
    /** Human name of a [androidx.work.WorkInfo] stop reason code. */
    fun name(code: Int): String = when (code) {
        WorkInfo.STOP_REASON_NOT_STOPPED -> "NOT_STOPPED"
        WorkInfo.STOP_REASON_UNKNOWN -> "UNKNOWN"
        WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
        WorkInfo.STOP_REASON_PREEMPT -> "PREEMPT"
        WorkInfo.STOP_REASON_TIMEOUT -> "TIMEOUT"
        WorkInfo.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
        WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
        WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
        WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
        WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
        WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
        WorkInfo.STOP_REASON_QUOTA -> "QUOTA"
        WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
        WorkInfo.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
        WorkInfo.STOP_REASON_USER -> "USER"
        WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
        WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
        else -> "CODE_$code"
    }
}
