package app.cairn.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cairn.sync.SyncIndicators
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Redraws every widget from what is already stored (Room), with no network and no constraints, so the freshness dot and age keep moving
 * while offline and a stuck "Syncing…" clears itself. Runs about every 15 minutes (WorkManager's minimum) and once just after a sync tap times out.
 */
class WidgetTickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { GlanceWidgetUpdater(applicationContext).updateAll() }
        return Result.success()
    }
}

object WidgetTicks {
    const val PERIODIC = "cairn-widget-tick"
    const val AFTER_TAP = "cairn-widget-tick-after-tap"
    const val INTERVAL_MIN = 15L

    /** Idempotent: keeps one unique periodic redraw. Safe to call on every widget enable, update and app start. */
    fun ensure(context: Context) {
        runCatching {
            val req = PeriodicWorkRequest.Builder(WidgetTickWorker::class.java, INTERVAL_MIN, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    /** Clears "Syncing…" when no result ever arrives (killed worker, no network): one redraw just after the syncing window ends. */
    fun afterSyncTap(context: Context) {
        runCatching {
            val req = OneTimeWorkRequest.Builder(WidgetTickWorker::class.java)
                .setInitialDelay(SyncIndicators.SYNCING_WINDOW.seconds + 5, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(AFTER_TAP, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Redraws the widgets when something they bake into their layout changes: font scale, dark mode, locale (configuration), the clock, time zone or date. */
class WidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                runCatching { GlanceWidgetUpdater(app).updateAll() }
                WidgetTicks.ensure(app)
            } finally { pending.finish() }
        }
    }
}
