package app.cairn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cairn.core.designsystem.FitMath
import app.cairn.core.designsystem.Spacing

/**
 * A value that always fits its width: the size steps down to [floor] first, and only below that does it wrap onto another
 * line. It is never ellipsized. Size is measured with the real font at the user's font scale.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    floor: TextUnit = 14.sp,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxPx = constraints.maxWidth.toFloat()
        val size = remember(text, style, maxPx, density) {
            if (!constraints.hasBoundedWidth) style.fontSize.value
            else FitMath.valueSize(
                { s -> measurer.measure(text, style.copy(fontSize = s.sp), softWrap = false, maxLines = 1).size.width.toFloat() },
                maxPx, style.fontSize.value, minOf(floor.value, style.fontSize.value),
            )
        }
        Text(text, style = style.copy(fontSize = size.sp), color = color, textAlign = textAlign)
    }
}

/**
 * Metric tiles that size to their content: the column count is the most that fit the widest value (4, 2, then 1), rows are
 * balanced (no orphan), and each tile takes an equal share of the full width. Heights follow the content.
 */
@Composable
fun AdaptiveTiles(items: List<MetricUi>, modifier: Modifier = Modifier, maxCols: Int = 4) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueStyle = MaterialTheme.typography.titleMedium
    val labelStyle = MaterialTheme.typography.labelMedium
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = Spacing.xs
        val padPx = with(density) { (Spacing.sm * 2).toPx() }
        val avail = with(density) { maxWidth.toPx() }
        val needs = remember(items, valueStyle, labelStyle, density) {
            items.map { maxOf(measurer.measure(it.value, valueStyle, softWrap = false).size.width, measurer.measure(it.label, labelStyle, softWrap = false).size.width) + padPx }
        }
        val cols = FitMath.columns(avail, needs, with(density) { gap.toPx() }, maxCols)
        var idx = 0
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            FitMath.rows(items.size, cols).forEach { n ->
                val line = items.subList(idx, idx + n); idx += n
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    line.forEach { m ->
                        Column(
                            Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            Text(m.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            FitText(m.value, valueStyle, Modifier.fillMaxWidth(), floor = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
