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
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterUi

/** Haptics only for meaningful commits: a finished refresh, a confirmed delete. */
fun View.confirmHaptic() { performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
fun View.rejectHaptic() { performHapticFeedback(HapticFeedbackConstants.REJECT) }

/** Data age with explicit wording; colour is a second channel, never the only one. */
@Composable
fun AgeLine(footer: FooterUi, modifier: Modifier = Modifier) {
    val (color, suffix) = when (footer.tier) {
        Freshness.Fresh -> MaterialTheme.colorScheme.onSurfaceVariant to ""
        Freshness.Aging -> MaterialTheme.colorScheme.tertiary to ", getting old"
        Freshness.Stale -> MaterialTheme.colorScheme.error to ""
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        androidx.compose.foundation.layout.Box(Modifier.size(Spacing.xs).background(color, CircleShape))
        Text(footer.text + suffix, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

/** Label on the left, value on the right; both wrap at large font scale. */
@Composable
fun MetricRow(m: MetricUi, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(m.value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
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
