package app.cairn

import android.content.Context
import app.cairn.demo.DemoMode
import kotlinx.coroutines.flow.StateFlow

/** Debug facade for demo mode; see [DemoMode]. */
object DemoFeature {
    const val available = true
    val active: Boolean get() = DemoMode.enabled
    val flow: StateFlow<Boolean> get() = DemoMode.flow
    fun install(graph: AppGraph, context: Context) = DemoMode.install(graph, context)
    fun set(graph: AppGraph, context: Context, on: Boolean) = DemoMode.set(graph, context, on)
}
