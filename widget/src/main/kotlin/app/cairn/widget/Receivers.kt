package app.cairn.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Base for the six receivers. Receiver class names stay stable; this only adds cleanup of a removed widget's saved choices. */
abstract class CairnReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTicks.ensure(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetTicks.ensure(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetLog.options(context, appWidgetId, "optionsChanged ${this::class.java.simpleName}")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val settings = WidgetEnvironment.settings ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { settings.clear(appWidgetIds) } finally { pending.finish() }
        }
    }
}
