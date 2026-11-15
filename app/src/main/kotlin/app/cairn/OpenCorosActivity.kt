package app.cairn

import android.app.Activity
import android.os.Bundle
import app.cairn.widget.EXTRA_WIDGET
import app.cairn.widget.Screen
import app.cairn.widget.TapTarget
import app.cairn.widget.WidgetKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Invisible trampoline for widget taps. With a widget extra it opens that widget's chosen target, otherwise COROS
 * (the stale footer). Schedules the delayed refreshes when COROS opens; falls back to Cairn when it cannot.
 */
class OpenCorosActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = WidgetKind.parse(intent?.getStringExtra(EXTRA_WIDGET))
        val target = if (kind == null) TapTarget.CorosHome else resolveTarget(kind)
        // The widget was tapped: sync Cairn right away (no gap) in the background, on top of the delayed follow-ups the launcher schedules.
        runCatching {
            val graph = (applicationContext as CairnApp).graph
            graph.appScope.launch { runCatching { graph.syncScheduler.requestUserSync() } }
        }
        CorosLauncher(this).launch(target, kind?.cairnScreen ?: Screen.Freshness)
        finish()
    }

    /** One short DataStore read; the tap must start the next activity from this foreground activity. */
    private fun resolveTarget(kind: WidgetKind): TapTarget = runCatching {
        val settings = (applicationContext as CairnApp).graph.appSettings
        runBlocking { withTimeoutOrNull(1_000) { settings.tapTarget(kind).first() } }
    }.getOrNull() ?: app.cairn.widget.TapTargets.default(kind)
}

fun isCorosInstalled(context: android.content.Context): Boolean = CorosLauncher(context).isInstalled()

/** Opens COROS (or Cairn's explanation when it is missing) and schedules the delayed refreshes. */
fun openCorosAndRefresh(context: android.content.Context) {
    CorosLauncher(context).launch(TapTarget.CorosHome, Screen.Freshness)
}
