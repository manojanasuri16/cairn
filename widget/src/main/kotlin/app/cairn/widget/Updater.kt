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
        HrvWidget().updateAll(context)
        HeartRateWidget().updateAll(context)
        StressWidget().updateAll(context)
        WeeklyMileageWidget().updateAll(context)
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
            WidgetKind.Hrv -> HrvWidget().updateAll(context)
            WidgetKind.HeartRate -> HeartRateWidget().updateAll(context)
            WidgetKind.Stress -> StressWidget().updateAll(context)
            WidgetKind.WeeklyMileage -> WeeklyMileageWidget().updateAll(context)
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
    Overview("Overview", OverviewWidgetReceiver::class.java, WidgetKind.Overview),
    Hrv("Overnight HRV", HrvWidgetReceiver::class.java, WidgetKind.Hrv),
    HeartRate("Heart rate", HeartRateWidgetReceiver::class.java, WidgetKind.HeartRate),
    Stress("Stress", StressWidgetReceiver::class.java, WidgetKind.Stress),
    WeeklyMileage("Weekly mileage", WeeklyMileageWidgetReceiver::class.java, WidgetKind.WeeklyMileage);

    /** "Ring, Gauge arc, Number and pill": the styles on offer, shown under the add button. */
    val styles: String get() = WidgetStyle.forKind(kind).joinToString(", ") { it.label }
}

object WidgetPinner {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    const val EXTRA_PIN_KIND = "cairn.pin.kind"
    const val EXTRA_PIN_SIZE = "cairn.pin.size"

    /** Old entry point: pins the widget at its natural size with default choices. */
    fun pin(context: Context, widget: PinnableWidget): Boolean = pin(context, widget.kind, WidgetVariants.defaultSize(widget.kind), null)

    /**
     * Asks the launcher to pin the provider variant whose default cell size is [size]. [config] (when given) is stored in a
     * pending-pin record that the new widget claims, so it appears configured. The success callback goes to [WidgetPinnedReceiver].
     */
    fun pin(context: Context, kind: WidgetKind, size: WidgetSize, config: WidgetConfig?): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        if (config != null) PendingPins(SharedPrefsPinKv(context)).put(PendingPin(kind, size, config, System.currentTimeMillis()))
        val callback = android.app.PendingIntent.getBroadcast(
            context, kind.ordinal * 10 + size.ordinal,
            Intent(context, WidgetPinnedReceiver::class.java).putExtra(EXTRA_PIN_KIND, kind.name).putExtra(EXTRA_PIN_SIZE, size.name),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        return mgr.requestPinAppWidget(ComponentName(context, WidgetVariants.receiverClass(kind, size)), null, callback)
    }
}
