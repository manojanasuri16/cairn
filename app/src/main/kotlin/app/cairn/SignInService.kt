package app.cairn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Short-lived foreground service that only exists to keep the process un-frozen while the loopback receiver
 * waits for the browser redirect ("local method"). It does no work itself; the coordinator owns the socket.
 * Runs even when notifications are denied. Stops on success, cancel, timeout, or after [MAX_MS].
 */
class SignInService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sign-in", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Signing in to COROS")
            .setOngoing(true)
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, MAX_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    companion object {
        private const val CHANNEL = "signin"
        private const val ID = 4207
        private const val MAX_MS = 6 * 60_000L

        fun start(context: Context) {
            try { ContextCompat.startForegroundService(context, Intent(context, SignInService::class.java)) } catch (_: Exception) { /* best effort */ }
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, SignInService::class.java)) } catch (_: Exception) { }
        }
    }
}
