package app.cairn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.Spacing
import app.cairn.openCorosAndRefresh

/** What a long press on a card offers: put that widget on the home screen (preset to the card's style), or open COROS (which also schedules a refresh). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardActionsSheet(link: WidgetLink, onAddToHome: (WidgetLink) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Text(link.kind.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
            ActionRow(AppIcons.Add, "Add to home screen", "Choose its size and look, then place it") { onDismiss(); onAddToHome(link) }
            ActionRow(AppIcons.Open, "Open in COROS", "Cairn refreshes shortly after") { onDismiss(); openCorosAndRefresh(context) }
        }
    }
}

@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
