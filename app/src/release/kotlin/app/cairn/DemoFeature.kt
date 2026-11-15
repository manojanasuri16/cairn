package app.cairn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Release stub: the screenshot data mode exists only in debug builds. */
object DemoFeature {
    const val available = false
    val active: Boolean get() = false
    val flow: StateFlow<Boolean> = MutableStateFlow(false)
    fun install(graph: AppGraph, context: Context) = Unit
    fun set(graph: AppGraph, context: Context, on: Boolean) = Unit
}
