package app.cairn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.cairn.sync.StaleNotifier
import app.cairn.widget.openCorosIntent

/** One low-importance notification; no health values in the text. Tap opens COROS and schedules refreshes. */
class StaleNudgeNotifier(private val context: Context) : StaleNotifier {
    companion object { const val CHANNEL = "stale_nudge"; const val ID = 4001 }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun show(ageHours: Long) {
        if (!hasPermission()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "COROS sync reminders", NotificationManager.IMPORTANCE_LOW))
        val tap = PendingIntent.getActivity(
            context, 0, openCorosIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("COROS hasn't synced")
            .setContentText("Last data is about $ageHours h old. Open COROS to sync your watch.")
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        nm.notify(ID, n)
    }

    override fun cancel() { context.getSystemService(NotificationManager::class.java).cancel(ID) }
}
