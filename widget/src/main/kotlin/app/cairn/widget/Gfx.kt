package app.cairn.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import app.cairn.core.designsystem.Contrast

/**
 * Everything Glance cannot draw itself (arcs, gradients, charts) is drawn here at exact device pixels and cached by value,
 * so a re-render with unchanged data costs nothing.
 */
object Gfx {
    private val cache = LruCache<Any, Bitmap>(32)

    private inline fun cached(key: Any, draw: () -> Bitmap): Bitmap =
        synchronized(cache) { cache.get(key) } ?: draw().also { synchronized(cache) { cache.put(key, it) } }

    private fun bmp(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun stroke(px: Int) = paint().apply { style = Paint.Style.STROKE; strokeWidth = px.toFloat(); strokeCap = Paint.Cap.ROUND }

    /** Card background: a diagonal gradient with a soft highlight. Tiny, because the system corner clip and FillBounds do the rest. */
    private data class BgKey(val start: Int, val center: Int, val end: Int, val alpha: Int)

    fun background(start: Int, center: Int, end: Int, opacityPct: Int): Bitmap = cached(BgKey(start, center, end, opacityPct)) {
        val n = 64
        val b = bmp(n, n)
        val c = Canvas(b)
        val a = (opacityPct.coerceIn(0, 100) * 255 / 100)
        fun withA(color: Int, factor: Float = 1f) = (color and 0xFFFFFF) or (((a * factor).toInt().coerceIn(0, 255)) shl 24)
        val p = paint()
        p.shader = LinearGradient(0f, 0f, n.toFloat(), n.toFloat(), intArrayOf(withA(start), withA(center), withA(end)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        val glow = paint()
        glow.shader = RadialGradient(n * 0.85f, n * 0.1f, n * 0.75f, withA(0xFFFFFFFF.toInt(), 0.08f), 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), glow)
        b
    }

    private data class RingKey(val px: Int, val stroke: Int, val pct: Int, val fg: Int, val track: Int)

    fun ring(sizePx: Int, strokePx: Int, fraction: Float, progress: Int, track: Int): Bitmap =
        cached(RingKey(sizePx, strokePx, (fraction.coerceIn(0f, 1f) * 100).toInt(), progress, track)) {
            val b = bmp(sizePx, sizePx)
            val c = Canvas(b)
            val half = strokePx / 2f
            val r = RectF(half, half, sizePx - half, sizePx - half)
            val p = stroke(strokePx)
            p.color = track
            c.drawArc(r, 0f, 360f, false, p)
            val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
            if (pct > 0) { p.color = progress; c.drawArc(r, -90f, 360f * pct / 100f, false, p) }
            b
        }

    private data class GaugeKey(val w: Int, val stroke: Int, val pct: Int, val fg: Int, val track: Int)

    /** Semi-circle gauge, [widthPx] wide and just over half as tall. */
    fun gauge(widthPx: Int, strokePx: Int, fraction: Float, progress: Int, track: Int): Bitmap =
        cached(GaugeKey(widthPx, strokePx, (fraction.coerceIn(0f, 1f) * 100).toInt(), progress, track)) {
            val h = widthPx / 2 + strokePx / 2 + 2
            val b = bmp(widthPx, h)
            val c = Canvas(b)
            val half = strokePx / 2f
            val r = RectF(half, half, widthPx - half, widthPx - half)
            val p = stroke(strokePx)
            p.color = track
            c.drawArc(r, 180f, 180f, false, p)
            val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
            if (pct > 0) { p.color = progress; c.drawArc(r, 180f, 180f * pct / 100f, false, p) }
            b
        }

    /** Gauge bitmap height for a given width, so layouts can reserve it. */
    fun gaugeHeight(widthPx: Int, strokePx: Int) = widthPx / 2 + strokePx / 2 + 2

    private data class DialKey(val px: Int, val stroke: Int, val ratio100: Int, val zones: List<Int>, val marker: Int, val markerRing: Int)

    /** 270 degree dial across ratio 0..2 in four zones (cool, good, warn, bad) with a marker at [ratio]. */
    fun dial(sizePx: Int, strokePx: Int, ratio: Double?, zones: List<Int>, marker: Int, markerRing: Int): Bitmap =
        cached(DialKey(sizePx, strokePx, ((ratio ?: -1.0) * 100).toInt(), zones, marker, markerRing)) {
            val b = bmp(sizePx, sizePx)
            val c = Canvas(b)
            val inset = strokePx / 2f + strokePx * 0.35f
            val r = RectF(inset, inset, sizePx - inset, sizePx - inset)
            val p = stroke(strokePx).apply { strokeCap = Paint.Cap.BUTT }
            val bounds = listOf(0.0, 0.8, 1.3, 1.5, 2.0)
            val total = 270f
            for (i in 0 until 4) {
                p.color = zones[i]
                val a0 = 135f + (bounds[i] / 2.0 * total).toFloat()
                val sweep = ((bounds[i + 1] - bounds[i]) / 2.0 * total).toFloat() - 2f
                c.drawArc(r, a0 + 1f, sweep, false, p)
            }
            if (ratio != null) {
                val ang = Math.toRadians((135.0 + (ratio.coerceIn(0.0, 2.0) / 2.0) * total))
                val cx = r.centerX() + (r.width() / 2f) * Math.cos(ang).toFloat()
                val cy = r.centerY() + (r.height() / 2f) * Math.sin(ang).toFloat()
                val f = paint()
                f.color = markerRing; c.drawCircle(cx, cy, strokePx * 0.95f, f)
                f.color = marker; c.drawCircle(cx, cy, strokePx * 0.6f, f)
            }
            b
        }

    private data class BarsKey(val w: Int, val h: Int, val pct: List<Int>, val today: Int, val color: Int, val dim: Int)

    /** Day bars; the one at [todayIdx] uses [color], others [dim]. Empty days keep a small stub so "no load" reads as a day, not a gap. */
    fun bars(wPx: Int, hPx: Int, fractions: List<Float>, todayIdx: Int, color: Int, dim: Int): Bitmap =
        cached(BarsKey(wPx, hPx, fractions.map { (it.coerceIn(0f, 1f) * 100).toInt() }, todayIdx, color, dim)) {
            val b = bmp(wPx, hPx)
            val c = Canvas(b)
            val n = fractions.size.coerceAtLeast(1)
            val slot = b.width.toFloat() / n
            val barW = slot * 0.56f
            val stub = (b.height * 0.05f).coerceAtLeast(3f)
            val p = paint()
            fractions.forEachIndexed { i, f ->
                val bh = (b.height * f.coerceIn(0f, 1f)).coerceAtLeast(stub)
                val left = i * slot + (slot - barW) / 2f
                p.color = if (i == todayIdx) color else dim
                c.drawRoundRect(RectF(left, b.height - bh, left + barW, b.height.toFloat()), barW / 3f, barW / 3f, p)
            }
            b
        }

    private data class TrendKey(val w: Int, val h: Int, val pct: List<Int>, val today: Int, val line: Int, val fill: Int, val dot: Int, val dotRing: Int)

    /** Smooth-ish line over the day values with a soft area under it and a dot on today. */
    fun trend(wPx: Int, hPx: Int, fractions: List<Float>, todayIdx: Int, line: Int, dot: Int, dotRing: Int): Bitmap =
        cached(TrendKey(wPx, hPx, fractions.map { (it.coerceIn(0f, 1f) * 100).toInt() }, todayIdx, line, Contrast.withAlpha(line, 0.28f), dot, dotRing)) {
            val b = bmp(wPx, hPx)
            val c = Canvas(b)
            val n = fractions.size
            val sw = (b.height * 0.045f).coerceAtLeast(3f)
            val padX = sw * 2.2f
            val padY = sw * 2.2f
            fun x(i: Int) = padX + (b.width - 2 * padX) * (if (n <= 1) 0.5f else i / (n - 1f))
            fun y(f: Float) = b.height - padY - (b.height - 2 * padY) * f.coerceIn(0f, 1f)
            if (n >= 2) {
                val path = Path().apply { moveTo(x(0), y(fractions[0])); for (i in 1 until n) {
                    val mx = (x(i - 1) + x(i)) / 2f
                    cubicTo(mx, y(fractions[i - 1]), mx, y(fractions[i]), x(i), y(fractions[i]))
                } }
                val area = Path(path).apply { lineTo(x(n - 1), b.height.toFloat()); lineTo(x(0), b.height.toFloat()); close() }
                val fp = paint(); fp.style = Paint.Style.FILL
                fp.shader = LinearGradient(0f, 0f, 0f, b.height.toFloat(), Contrast.withAlpha(line, 0.35f), Contrast.withAlpha(line, 0f), Shader.TileMode.CLAMP)
                c.drawPath(area, fp)
                val lp = stroke(sw.toInt().coerceAtLeast(2)); lp.color = line; lp.strokeJoin = Paint.Join.ROUND
                c.drawPath(path, lp)
            }
            if (todayIdx in 0 until n) {
                val dp = paint(); dp.color = dotRing; c.drawCircle(x(todayIdx), y(fractions[todayIdx]), sw * 2.1f, dp)
                dp.color = dot; c.drawCircle(x(todayIdx), y(fractions[todayIdx]), sw * 1.35f, dp)
            }
            b
        }

    private data class HBarKey(val w: Int, val h: Int, val pct: Int, val fg: Int, val track: Int)

    /** Rounded horizontal bar filled to [fraction]. */
    fun hbar(wPx: Int, hPx: Int, fraction: Float, fg: Int, track: Int): Bitmap =
        cached(HBarKey(wPx, hPx, (fraction.coerceIn(0f, 1f) * 100).toInt(), fg, track)) {
            val b = bmp(wPx, hPx)
            val c = Canvas(b)
            val p = paint()
            val r = b.height / 2f
            p.color = track
            c.drawRoundRect(RectF(0f, 0f, b.width.toFloat(), b.height.toFloat()), r, r, p)
            val fw = b.width * (fraction.coerceIn(0f, 1f))
            if (fw > 0f) { p.color = fg; c.drawRoundRect(RectF(0f, 0f, fw.coerceAtLeast(b.height.toFloat()), b.height.toFloat()), r, r, p) }
            b
        }

    private data class StageKey(val w: Int, val h: Int, val weights: List<Int>, val colors: List<Int>)

    /** One rounded bar split into segments proportional to [weights]. */
    fun stageBar(wPx: Int, hPx: Int, weights: List<Int>, colors: List<Int>): Bitmap = cached(StageKey(wPx, hPx, weights, colors)) {
        val b = bmp(wPx, hPx)
        val c = Canvas(b)
        val total = weights.sum().coerceAtLeast(1).toFloat()
        val gap = 2f
        val p = paint()
        var x = 0f
        val usable = b.width - gap * (weights.size - 1).coerceAtLeast(0)
        weights.forEachIndexed { i, w ->
            val segW = usable * w / total
            p.color = colors[i]
            c.drawRoundRect(RectF(x, 0f, x + segW, b.height.toFloat()), b.height / 2f, b.height / 2f, p)
            x += segW + gap
        }
        b
    }

    private data class RangeKey(val w: Int, val h: Int, val band: Pair<Int, Int>?, val marker: Int, val colors: List<Int>)

    /**
     * A horizontal range bar: a rounded track, the shaded [band] (0..1 start and end, e.g. the normal range) and a vertical marker
     * pin at [marker] (0..1) with a ring in the card colour so it reads over the band. Colours: track, band, marker, markerRing.
     */
    fun rangeBar(wPx: Int, hPx: Int, band: Pair<Float, Float>?, marker: Float, track: Int, bandColor: Int, markerColor: Int, ring: Int): Bitmap =
        cached(RangeKey(wPx, hPx, band?.let { (it.first.coerceIn(0f, 1f) * 1000).toInt() to (it.second.coerceIn(0f, 1f) * 1000).toInt() }, (marker.coerceIn(0f, 1f) * 1000).toInt(), listOf(track, bandColor, markerColor, ring))) {
            val b = bmp(wPx, hPx)
            val c = Canvas(b)
            val p = paint()
            val trackH = b.height * 0.5f
            val top = (b.height - trackH) / 2f
            p.color = track
            c.drawRoundRect(RectF(0f, top, b.width.toFloat(), top + trackH), trackH / 2f, trackH / 2f, p)
            if (band != null) {
                p.color = bandColor
                val l = b.width * band.first.coerceIn(0f, 1f)
                val r = (b.width * band.second.coerceIn(0f, 1f)).coerceAtLeast(l + trackH)
                c.drawRoundRect(RectF(l, top, r.coerceAtMost(b.width.toFloat()), top + trackH), trackH / 2f, trackH / 2f, p)
            }
            val pinW = (b.height * 0.26f).coerceAtLeast(4f)
            val cx = (b.width * marker.coerceIn(0f, 1f)).coerceIn(pinW, b.width - pinW)
            p.color = ring
            c.drawRoundRect(RectF(cx - pinW * 0.5f - 2f, 0f, cx + pinW * 0.5f + 2f, b.height.toFloat()), pinW, pinW, p)
            p.color = markerColor
            c.drawRoundRect(RectF(cx - pinW * 0.5f, 2f, cx + pinW * 0.5f, b.height - 2f), pinW, pinW, p)
            b
        }

    private data class BandBarsKey(val w: Int, val h: Int, val pct: List<Int>, val colors: List<Int>, val today: Int, val band: Pair<Int, Int>?, val extra: List<Int>)

    /**
     * Day bars with an optional shaded horizontal [band] behind them (0..1 of the height, e.g. the normal range) and a colour per bar.
     * A fraction below 0 means "no reading": a small neutral stub marks the day. The bar at [todayIdx] gets an outline in [todayRing].
     */
    fun bandBars(wPx: Int, hPx: Int, fractions: List<Float>, colors: List<Int>, todayIdx: Int, todayRing: Int, band: Pair<Float, Float>?, bandColor: Int, stubColor: Int): Bitmap =
        cached(BandBarsKey(wPx, hPx, fractions.map { if (it < 0f) -1 else (it.coerceIn(0f, 1f) * 100).toInt() }, colors, todayIdx, band?.let { (it.first.coerceIn(0f, 1f) * 100).toInt() to (it.second.coerceIn(0f, 1f) * 100).toInt() }, listOf(todayRing, bandColor, stubColor))) {
            val b = bmp(wPx, hPx)
            val c = Canvas(b)
            val n = fractions.size.coerceAtLeast(1)
            val slot = b.width.toFloat() / n
            val barW = slot * 0.56f
            val p = paint()
            if (band != null) {
                p.color = bandColor
                val top = b.height * (1f - band.second.coerceIn(0f, 1f))
                val bottom = b.height * (1f - band.first.coerceIn(0f, 1f))
                c.drawRoundRect(RectF(0f, top, b.width.toFloat(), bottom.coerceAtLeast(top + 2f)), 6f, 6f, p)
            }
            val stub = (b.height * 0.05f).coerceAtLeast(3f)
            fractions.forEachIndexed { i, f ->
                val left = i * slot + (slot - barW) / 2f
                if (f < 0f) {
                    p.color = stubColor
                    c.drawRoundRect(RectF(left, b.height - stub, left + barW, b.height.toFloat()), barW / 3f, barW / 3f, p)
                } else {
                    val bh = (b.height * f.coerceIn(0f, 1f)).coerceAtLeast(stub)
                    val r = RectF(left, b.height - bh, left + barW, b.height.toFloat())
                    p.style = Paint.Style.FILL
                    p.color = colors[i]
                    c.drawRoundRect(r, barW / 3f, barW / 3f, p)
                    if (i == todayIdx) {
                        val o = paint(); o.style = Paint.Style.STROKE; o.strokeWidth = 2f; o.color = todayRing
                        c.drawRoundRect(RectF(r.left - 1f, r.top - 1f, r.right + 1f, r.bottom), barW / 3f, barW / 3f, o)
                    }
                }
            }
            b
        }
}
