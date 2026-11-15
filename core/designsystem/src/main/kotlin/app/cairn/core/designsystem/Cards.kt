package app.cairn.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The palette's own gradient (same two stops the widgets use), 135 degrees. */
fun Palette.gradientBrush(): Brush = Brush.linearGradient(listOf(Color(start), Color(center), Color(end)))

/** One hero per screen area: a vivid gradient card whose content uses [Palette.ink]. */
@Composable
fun HeroSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalCairnPalette.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.gradientBrush()).padding(Spacing.md)) { content() }
}
