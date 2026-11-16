package app.cairn.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.HeroSurface
import app.cairn.core.designsystem.LocalCairnPalette
import app.cairn.core.designsystem.LocalCardColors
import app.cairn.core.designsystem.Spacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterUi

/** Haptics only for meaningful commits: a finished refresh, a confirmed delete. */
fun View.confirmHaptic() { performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
fun View.rejectHaptic() { performHapticFeedback(HapticFeedbackConstants.REJECT) }

/** Data age with explicit wording; colour is a second channel, never the only one. */
@Composable
fun AgeLine(footer: FooterUi, modifier: Modifier = Modifier) {
    val p = LocalCardColors.current
    val (dot, suffix) = when (footer.tier) {
        Freshness.Fresh -> Color(p.good) to ""
        Freshness.Aging -> Color(p.warn) to ", getting old"
        Freshness.Stale -> Color(p.bad) to ""
    }
    val bgArgb = MaterialTheme.colorScheme.background.toArgb()
    val color = if (footer.tier == Freshness.Stale) Color(Contrast.ensure(p.bad, listOf(bgArgb), 4.5)) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(Spacing.xs).background(dot, CircleShape))
        Text(footer.text + suffix, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Quiet section header: small, secondary text colour. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, onHero: Boolean = false) {
    Text(
        text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
        color = if (onHero) Color(LocalCairnPalette.current.ink) else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The one hero per screen: a vivid gradient card, content in the palette's ink. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, card: CardId? = null, content: @Composable () -> Unit) {
    HeroSurface(if (card != null) modifier.widgetActions(card) else modifier) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() } }
}

/** Solid semantic pill (level words stay in the text). */
@Composable
fun LevelPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color, RoundedCornerShape(50)).padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color(Contrast.bestInk(listOf(color.toArgb()))))
    }
}

/** Legend entry: coloured dot plus text in the normal text colour, so colour is never the only signal. */
@Composable
fun LegendItem(color: Color, text: String, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, card: CardId? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = cairnCardColors()) {
        // The gesture sits inside the card (before the padding) so the press ripple is clipped to the card's rounded corners.
        Column((if (card != null) Modifier.fillMaxWidth().widgetActions(card, onClick) else Modifier.fillMaxWidth()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

/** Label and value on one line when they fit; otherwise the value drops to its own line, whole. Never squeezed or ellipsized. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MetricRow(m: MetricUi, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(m.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(m.value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}

@Composable
fun EmptyState(e: EmptyUi, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(e.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(e.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (e.action == EmptyAction.OpenAccount) TextButton(onClick = onAction) { Text("Open COROS account") }
    }
}

/** White (light) or near-black (dark) cards on the neutral background, as in Daymark. */
@Composable
fun cairnCardColors() = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
