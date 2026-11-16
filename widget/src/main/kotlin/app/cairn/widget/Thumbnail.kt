package app.cairn.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize

/** Pure fit math for widget thumbnails: the slot follows the widget's own aspect ratio and the content is scaled, never re-flowed. */
object ThumbnailMath {
    const val WIDGET_CORNER_DP = 24f

    fun aspect(size: DpSize): Float = size.width.value / size.height.value

    /** Scale that fits a widget of [realWidthDp] into a slot [slotWidthDp] wide (never above 1). */
    fun scale(realWidthDp: Float, slotWidthDp: Float): Float = if (realWidthDp <= 0f) 1f else minOf(1f, slotWidthDp / realWidthDp).coerceAtLeast(0.05f)

    /** Corner radius of the slot: the widget's own radius, scaled with it. */
    fun cornerDp(scale: Float): Float = WIDGET_CORNER_DP * scale

    /** Bitmap width in px: the slot at device density, capped so many cached thumbnails stay small. */
    fun bitmapWidthPx(slotWidthDp: Float, density: Float, capPx: Int = 640): Int = (slotWidthDp * density).toInt().coerceIn(1, capPx)
}

object WidgetThumbnails {
    /**
     * The widget composed at its real [size] (same path as the editor preview), applied and laid out EXACTLY at that size.
     * Call on the main thread. The returned view is detached; draw it with [render].
     */
    suspend fun view(context: Context, kind: WidgetKind, cfg: ResolvedConfig, size: DpSize): View {
        val app = context.applicationContext
        val rv = WidgetCatalog.previewViews(app, kind, cfg, size)
        val d = app.resources.displayMetrics.density
        val w = (size.width.value * d).toInt(); val h = (size.height.value * d).toInt()
        val frame = FrameLayout(app)
        val v = rv.apply(app, frame)
        frame.addView(v, FrameLayout.LayoutParams(w, h))
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w, h)
        return frame
    }

    /** Draws [v] scaled to [widthPx] wide (aspect kept) into a small bitmap. */
    fun render(v: View, widthPx: Int): Bitmap {
        val s = widthPx.toFloat() / v.width.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, (v.height * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(s, s); v.draw(c)
        return bmp
    }
}
