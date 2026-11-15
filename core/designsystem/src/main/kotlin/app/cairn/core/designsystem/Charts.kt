package app.cairn.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Progress ring with the value in the middle. The ring is a decoration; [description] carries the meaning for TalkBack. */
@Composable
fun ProgressRing(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    stroke: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), CairnMotion.settle(reduced), label = "ring")
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** 7-point sparkline. Null values are gaps. Minimum height only, never a fixed text-bearing height. */
@Composable
fun Sparkline(
    values: List<Float?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val dim = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = description },
    ) {
        val present = values.filterNotNull()
        if (values.isEmpty()) return@Canvas
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val min = present.minOrNull() ?: 0f
        val max = present.maxOrNull() ?: 0f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        fun x(i: Int) = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
        fun y(v: Float) = pad + h - h * ((v - min) / span).let { if (max == min) 0.5f else it }
        drawLine(dim, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), 1.dp.toPx())
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            if (!open) { path.moveTo(x(i), y(v)); open = true } else path.lineTo(x(i), y(v))
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v -> if (v != null) drawCircle(color, if (i == values.lastIndex) 4.dp.toPx() else 2.dp.toPx(), Offset(x(i), y(v))) }
    }
}

/** Paired bars (short-term over long-term load) per day, scaled to the largest value. */
@Composable
fun LoadBars(
    short: List<Float?>,
    long: List<Float?>,
    description: String,
    modifier: Modifier = Modifier,
    shortColor: Color = MaterialTheme.colorScheme.primary,
    longColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    Canvas(modifier.fillMaxWidth().height(96.dp).sizeIn(minHeight = 96.dp).semantics { contentDescription = description }) {
        val max = (short + long).filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: return@Canvas
        val n = short.size.coerceAtLeast(1)
        val slot = size.width / n
        val bar = slot * 0.3f
        val base = size.height
        fun draw(v: Float?, left: Float, c: Color) {
            if (v == null) return
            val bh = (size.height * v / max).coerceAtLeast(2.dp.toPx())
            drawRoundRect(c, Offset(left, base - bh), Size(bar, bh), androidx.compose.ui.geometry.CornerRadius(bar / 4))
        }
        for (i in 0 until n) {
            val c = slot * i + slot / 2
            draw(short.getOrNull(i), c - bar - 1.dp.toPx(), shortColor)
            draw(long.getOrNull(i), c + 1.dp.toPx(), longColor)
        }
    }
}

/** One horizontal bar split into weighted segments (sleep stages). */
@Composable
fun SegmentBar(segments: List<Pair<Float, Color>>, description: String, modifier: Modifier = Modifier) {
    val gap = 2.dp
    Canvas(modifier.fillMaxWidth().height(12.dp).semantics { contentDescription = description }) {
        val total = segments.sumOf { it.first.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val g = gap.toPx()
        val usable = size.width - g * (segments.size - 1).coerceAtLeast(0)
        var x = 0f
        segments.forEach { (v, c) ->
            val w = usable * v / total
            if (w > 0f) drawRoundRect(c, Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            x += w + g
        }
    }
}
