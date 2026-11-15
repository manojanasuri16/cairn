package app.cairn.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
        OverviewWidget().updateAll(context)
    }

    /** Re-renders one widget kind after its configuration changed. */
    suspend fun update(kind: WidgetKind) {
        when (kind) {
            WidgetKind.Today -> TodayWidget().updateAll(context)
            WidgetKind.Recovery -> RecoveryWidget().updateAll(context)
            WidgetKind.LastActivity -> LastActivityWidget().updateAll(context)
            WidgetKind.TrainingLoad -> TrainingLoadWidget().updateAll(context)
            WidgetKind.Sleep -> SleepWidget().updateAll(context)
            WidgetKind.Fitness -> FitnessWidget().updateAll(context)
            WidgetKind.Overview -> OverviewWidget().updateAll(context)
        }
    }
}

/** "Add widget" entries for the app. */
enum class PinnableWidget(val label: String, val receiver: Class<*>, val kind: WidgetKind) {
    Today("Today", TodayWidgetReceiver::class.java, WidgetKind.Today),
    Recovery("Recovery", RecoveryWidgetReceiver::class.java, WidgetKind.Recovery),
    LastActivity("Last activity", LastActivityWidgetReceiver::class.java, WidgetKind.LastActivity),
    TrainingLoad("Training load", TrainingLoadWidgetReceiver::class.java, WidgetKind.TrainingLoad),
    Sleep("Sleep", SleepWidgetReceiver::class.java, WidgetKind.Sleep),
    Fitness("Fitness", FitnessWidgetReceiver::class.java, WidgetKind.Fitness),
    Overview("Overview", OverviewWidgetReceiver::class.java, WidgetKind.Overview);

    /** "Ring, Gauge arc, Number and pill": the styles on offer, shown under the add button. */
    val styles: String get() = WidgetStyle.forKind(kind).joinToString(", ") { it.label }
}

object WidgetPinner {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * Asks the launcher to pin. The success callback goes to [WidgetPinnedReceiver], which opens the configuration screen
     * ourselves: launchers do not reliably do that for a pin they did not start from the widget picker.
     */
    fun pin(context: Context, widget: PinnableWidget): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        val callback = android.app.PendingIntent.getBroadcast(
            context, widget.ordinal, Intent(context, WidgetPinnedReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        return mgr.requestPinAppWidget(ComponentName(context, widget.receiver), null, callback)
    }
}
