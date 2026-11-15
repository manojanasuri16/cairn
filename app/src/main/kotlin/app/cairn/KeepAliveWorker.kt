package app.cairn

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Never meant to run. A far-future job keeps WorkManager with pending work, so it does not disable its receivers;
 * some launchers react to that by resetting every widget to its initial layout.
 */
class KeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val NAME = "cairn-keep-alive"
        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<KeepAliveWorker>().setInitialDelay(3650, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }
    }
}
