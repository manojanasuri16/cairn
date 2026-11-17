package app.cairn.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize

/** Pure fit math for widget thumbnails: the slot follows the widget's own aspect ratio and the content is scaled, never re-flowed. */
object ThumbnailMath {
    const val WIDGET_CORNER_DP = 24f

    fun aspect(size: DpSize): Float = size.width.value / size.height.value

    /** Never above 1: a thumbnail is shown at the widget's true size, or smaller when the slot is narrower. It is never enlarged. */
    fun scale(realWidthDp: Float, availWidthDp: Float): Float = if (realWidthDp <= 0f) 1f else minOf(1f, availWidthDp / realWidthDp).coerceAtLeast(0.05f)

    /** Corner radius of the slot: the widget's own radius, scaled with it. */
    fun cornerDp(scale: Float): Float = WIDGET_CORNER_DP * scale

    /** Exact pixels of the slot (and of the bitmap drawn into it) for [dp] at [density] and [scale]. Bitmap px == slot px, so nothing is resampled. */
    fun slotPx(dp: Float, density: Float, scale: Float): Int = Math.round(dp * density * scale).coerceAtLeast(1)

    /** Left offset that centres a slot of [slotDp] inside [containerDp]. */
    fun centreOffset(containerDp: Float, slotDp: Float): Float = ((containerDp - slotDp) / 2f).coerceAtLeast(0f)
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

    /** Draws [v] scaled to [widthPx] wide (aspect kept) into a small bitmap, with rounded outlines honoured (see [drawClipped]). */
    fun render(v: View, widthPx: Int): Bitmap {
        val s = widthPx.toFloat() / v.width.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, Math.round(v.height * s).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(s, s); drawClipped(v, c)
        return bmp
    }

    /** Corner radius (px) of a view that clips to a rounded outline (Glance's cornerRadius on Android 12+), or null. */
    internal fun outlineRadius(v: View): Float? {
        if (!v.clipToOutline || v.width <= 0 || v.height <= 0) return null
        val o = android.graphics.Outline()
        runCatching { v.outlineProvider?.getOutline(v, o) }
        if (o.isEmpty) return null
        return o.radius.takeIf { it >= 0f }
    }

    private fun clippedDescendants(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            val c = v.getChildAt(i)
            if (outlineRadius(c) != null) out += c else clippedDescendants(c, out)
        }
        return out
    }

    private fun offsetIn(root: View, v: View): Pair<Float, Float> {
        var x = 0f; var y = 0f; var cur: View = v
        while (cur !== root) { x += cur.left + cur.translationX; y += cur.top + cur.translationY; cur = cur.parent as? View ?: break }
        return x to y
    }

    /**
     * View.draw on a plain Canvas ignores clip-to-outline, which turned round dots and pills into squares. Views that clip to a rounded
     * outline are hidden while their parent draws, then drawn into their own layer, masked with a rounded rect and composited back.
     */
    fun drawClipped(root: View, canvas: Canvas) {
        val kids = clippedDescendants(root)
        val states = kids.map { it.visibility }
        kids.forEach { it.visibility = View.INVISIBLE }
        root.draw(canvas)
        kids.forEachIndexed { i, k -> k.visibility = states[i] }
        for (k in kids) {
            val r = outlineRadius(k) ?: continue
            val layer = Bitmap.createBitmap(k.width, k.height, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            drawClipped(k, lc)
            val mask = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
            val shape = Bitmap.createBitmap(k.width, k.height, Bitmap.Config.ARGB_8888)
            Canvas(shape).drawRoundRect(0f, 0f, k.width.toFloat(), k.height.toFloat(), r, r, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
            lc.drawBitmap(shape, 0f, 0f, mask)
            val (x, y) = offsetIn(root, k)
            canvas.drawBitmap(layer, x, y, null)
            layer.recycle(); shape.recycle()
        }
    }
}
