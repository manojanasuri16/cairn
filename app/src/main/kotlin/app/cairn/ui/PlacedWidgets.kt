package app.cairn.ui

import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetVariants

/** One widget the user has placed on a home screen. Choices only, never health data. */
data class PlacedWidget(val appWidgetId: Int, val kind: WidgetKind, val size: WidgetSize)

/** Pure mapping from "provider class name to the ids placed for it" to the "Your widgets" list. */
object PlacedWidgets {
    fun list(placed: List<Pair<String, IntArray>>): List<PlacedWidget> =
        placed.flatMap { (cls, ids) ->
            val kind = WidgetVariants.kindOf(cls) ?: return@flatMap emptyList()
            val size = WidgetVariants.sizeOf(cls) ?: WidgetVariants.defaultSize(kind)
            ids.map { PlacedWidget(it, kind, size) }
        }.distinctBy { it.appWidgetId }.sortedWith(compareBy({ it.kind.ordinal }, { it.size.ordinal }, { it.appWidgetId }))

}
