package app.cairn.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Pure rule for when to open the configuration screen after a pin. */
object ConfigLaunch {
    /** Launch only for a real widget id that has not shown its configuration yet (the launcher may already have opened it). */
    fun shouldLaunch(appWidgetId: Int, alreadyShown: Boolean): Boolean = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !alreadyShown
}

const val EXTRA_FROM_PIN = "cairn.from_pin"
const val CONFIG_ACTIVITY_CLASS = "app.cairn.WidgetConfigActivity"

/** Receives the pin-success callback (the system adds the new widget id) and opens that widget's configuration screen. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val settings = WidgetEnvironment.settings
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val shown = settings?.configShown(id) ?: false
                if (ConfigLaunch.shouldLaunch(id, shown)) {
                    app.startActivity(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setClassName(app.packageName, CONFIG_ACTIVITY_CLASS)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_FROM_PIN, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } finally { pending.finish() }
        }
    }
}
