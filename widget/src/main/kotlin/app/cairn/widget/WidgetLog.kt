package app.cairn.widget

import android.util.Log

/** Debug-only diagnostics for sizing and configuration. Logs ids, sizes, layout plans and stat NAMES only; never health values, tokens or text shown on a widget. */
object WidgetLog {
    const val TAG = "CairnWidget"
    @Volatile var enabled: Boolean = false
    fun d(message: String) { if (enabled) Log.d(TAG, message) }

    /** The size options the launcher reported for [appWidgetId] (min/max width and height and the per-orientation sizes). */
    fun options(context: android.content.Context, appWidgetId: Int, where: String) {
        if (!enabled) return
        runCatching {
            val o = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val sizes = o.getParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            d("options[$where] id=$appWidgetId minW=${o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} maxW=${o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} minH=${o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} maxH=${o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} sizes=${sizes?.map { "${it.width}x${it.height}" }}")
        }
    }
}
