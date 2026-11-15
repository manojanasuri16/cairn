package app.cairn.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.appwidget.updateAll
import app.cairn.sync.WidgetUpdater

/** Re-renders every Cairn widget; called by the sync engine after each run. */
class GlanceWidgetUpdater(private val context: Context) : WidgetUpdater {
    override suspend fun updateAll() {
        WidgetEnvironment.ticks.emit(Unit)
        TodayWidget().updateAll(context)
        RecoveryWidget().updateAll(context)
        LastActivityWidget().updateAll(context)
        TrainingLoadWidget().updateAll(context)
        SleepWidget().updateAll(context)
        FitnessWidget().updateAll(context)
    }
}

/** "Add widget" entries for the app. */
enum class PinnableWidget(val label: String, val receiver: Class<*>) {
    Today("Today", TodayWidgetReceiver::class.java),
    Recovery("Recovery", RecoveryWidgetReceiver::class.java),
    LastActivity("Last activity", LastActivityWidgetReceiver::class.java),
    TrainingLoad("Training load", TrainingLoadWidgetReceiver::class.java),
    Sleep("Sleep", SleepWidgetReceiver::class.java),
    Fitness("Fitness", FitnessWidgetReceiver::class.java),
}

object WidgetPinner {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Asks the launcher to pin; returns false when unsupported. */
    fun pin(context: Context, widget: PinnableWidget): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(context, widget.receiver), null, null)
    }
}
