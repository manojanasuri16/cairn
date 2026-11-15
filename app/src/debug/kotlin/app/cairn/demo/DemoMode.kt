package app.cairn.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cairn.AppGraph
import app.cairn.CairnApp
import app.cairn.sync.SyncState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Debug-only demo mode: replaces the Room tables with the fictional [DemoDataset] and suppresses sync, so screenshots never
 * show the owner's real health data. Turning it off wipes the demo rows; a normal sync refills them.
 */
object DemoMode {
    const val NOTE = "Demo mode"
    private const val PREFS = "cairn_demo"
    private const val KEY = "enabled"

    @Volatile var enabled = false
        private set
    private val _flow = MutableStateFlow(false)
    val flow: StateFlow<Boolean> = _flow

    fun install(graph: AppGraph, context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        _flow.value = enabled
        graph.syncEngine.suppress = { if (enabled) NOTE else null }
    }

    fun set(graph: AppGraph, context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).commit()
        enabled = on
        _flow.value = on
        graph.appScope.launch { runCatching { apply(graph, on) } }
    }

    private suspend fun apply(graph: AppGraph, on: Boolean) {
        graph.deleteAllData()
        if (!on) { graph.syncScheduler.requestUserSync(); return }
        val now = Instant.now()
        val d = DemoDataset.build(LocalDate.now(), now)
        graph.dailyRepository.store(d.daily); graph.sleepRepository.store(d.sleep)
        graph.recoveryRepository.store(d.recovery); graph.trainingLoadRepository.store(d.load)
        graph.fitnessRepository.store(d.fitness); graph.activityRepository.store(d.activities)
        graph.vitalsRepository.store(d.vitals); graph.hrvRepository.store(d.hrv)
        graph.activityDetailRepository.store(d.details)
        val ms = now.toEpochMilli()
        graph.syncState.update { SyncState(lastAttemptMs = ms, lastSuccessMs = ms, lastMcpRunMs = ms, lastMcpDataChangeMs = ms, lastHcDataMs = ms) }
        graph.refreshWidgets()
    }
}

/**
 * `adb shell am broadcast -n app.cairn/.demo.DemoReceiver -a app.cairn.DEMO --ez enabled true` (or false).
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.cairn.DEMO") return
        val graph = (context.applicationContext as CairnApp).graph
        DemoMode.install(graph, context.applicationContext)
        DemoMode.set(graph, context.applicationContext, intent.getBooleanExtra("enabled", true))
    }
}
