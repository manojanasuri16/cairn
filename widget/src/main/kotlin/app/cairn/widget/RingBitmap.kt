package app.cairn.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache

/**
 * Draws a progress ring as a bitmap at exact device pixels (crisp on dense screens). Glance has no
 * arc primitive, so this is the supported route. Cached by value so re-renders are free.
 */
object RingBitmap {
    private data class Key(val px: Int, val strokePx: Int, val percent: Int, val progress: Int, val track: Int)

    private val cache = LruCache<Key, Bitmap>(8)

    fun get(sizePx: Int, strokePx: Int, fraction: Float, progressColor: Int, trackColor: Int): Bitmap {
        val key = Key(sizePx, strokePx, (fraction.coerceIn(0f, 1f) * 100).toInt(), progressColor, trackColor)
        return synchronized(cache) { cache.get(key) } ?: draw(key).also { synchronized(cache) { cache.put(key, it) } }
    }

    private fun draw(k: Key): Bitmap {
        val bmp = Bitmap.createBitmap(k.px, k.px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val half = k.strokePx / 2f
        val rect = RectF(half, half, k.px - half, k.px - half)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = k.strokePx.toFloat(); strokeCap = Paint.Cap.ROUND }
        p.color = k.track
        c.drawArc(rect, 0f, 360f, false, p)
        if (k.percent > 0) {
            p.color = k.progress
            c.drawArc(rect, -90f, 360f * k.percent / 100f, false, p)
        }
        return bmp
    }
}
