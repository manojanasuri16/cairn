package app.cairn.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache

/** 7-day load bars and the sleep stage bar, drawn at exact device pixels and cached by value (Glance has no weighted or proportional boxes). */
object ChartBitmaps {
    private data class BarsKey(val w: Int, val h: Int, val heights: List<Int>, val todayIdx: Int, val color: Int, val dim: Int)
    private data class StageKey(val w: Int, val h: Int, val weights: List<Int>, val colors: List<Int>)

    private val bars = LruCache<BarsKey, Bitmap>(8)
    private val stages = LruCache<StageKey, Bitmap>(8)

    /** [fractions] 0..1 per bar; the bar at [todayIdx] uses [color], others [dimColor]. Zero-height bars still show a small stub so an empty day reads as "no load". */
    fun bars(wPx: Int, hPx: Int, fractions: List<Float>, todayIdx: Int, color: Int, dimColor: Int): Bitmap {
        val key = BarsKey(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), fractions.map { (it.coerceIn(0f, 1f) * 100).toInt() }, todayIdx, color, dimColor)
        return synchronized(bars) { bars.get(key) } ?: drawBars(key).also { synchronized(bars) { bars.put(key, it) } }
    }

    private fun drawBars(k: BarsKey): Bitmap {
        val bmp = Bitmap.createBitmap(k.w, k.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val n = k.heights.size.coerceAtLeast(1)
        val slot = k.w.toFloat() / n
        val barW = slot * 0.5f
        val stub = (k.h * 0.04f).coerceAtLeast(2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        k.heights.forEachIndexed { i, pct ->
            val bh = (k.h * pct / 100f).coerceAtLeast(stub)
            val left = i * slot + (slot - barW) / 2f
            p.color = if (i == k.todayIdx) k.color else k.dim
            c.drawRoundRect(RectF(left, k.h - bh, left + barW, k.h.toFloat()), barW / 3f, barW / 3f, p)
        }
        return bmp
    }

    /** One rounded bar split into segments proportional to [weights]. */
    fun stageBar(wPx: Int, hPx: Int, weights: List<Int>, colors: List<Int>): Bitmap {
        val key = StageKey(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), weights, colors)
        return synchronized(stages) { stages.get(key) } ?: drawStages(key).also { synchronized(stages) { stages.put(key, it) } }
    }

    private fun drawStages(k: StageKey): Bitmap {
        val bmp = Bitmap.createBitmap(k.w, k.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val total = k.weights.sum().coerceAtLeast(1).toFloat()
        val gap = 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = 0f
        val usable = k.w - gap * (k.weights.size - 1).coerceAtLeast(0)
        k.weights.forEachIndexed { i, w ->
            val segW = usable * w / total
            p.color = k.colors[i]
            c.drawRoundRect(RectF(x, 0f, x + segW, k.h.toFloat()), k.h / 2f, k.h / 2f, p)
            x += segW + gap
        }
        return bmp
    }
}
