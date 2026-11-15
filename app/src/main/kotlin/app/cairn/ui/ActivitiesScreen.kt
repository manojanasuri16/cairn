package app.cairn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterUi

@Composable
fun ActivitiesScreen(onOpenAccount: () -> Unit, modifier: Modifier = Modifier, vm: ActivitiesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    ActivitiesContent(state, onOpenAccount, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesContent(state: ActivitiesState, onOpenAccount: () -> Unit, modifier: Modifier = Modifier) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.rows.firstOrNull { it.id == selectedId }
    Box(modifier.fillMaxSize()) {
        if (state.empty != null) {
            EmptyState(state.empty, onOpenAccount, Modifier.padding(top = Spacing.xl))
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.footer?.let { item { AgeLine(it) } }
                items(state.rows, key = { it.id }) { row -> ActivityRow(row) { selectedId = row.id } }
            }
        }
    }
    if (selected != null) {
        // Material's sheet tracks the finger 1:1 and can be grabbed again mid-animation; it settles with its own spring.
        ModalBottomSheet(onDismissRequest = { selectedId = null }, sheetState = rememberModalBottomSheetState()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Spacing.md).padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(selected.sport, style = MaterialTheme.typography.headlineSmall)
                selected.details.forEach { MetricRow(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityRow(row: ActivityRowUi, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClickLabel = "Show details", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = row.description }) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp).clearAndSetSemantics { }) {
                Box(contentAlignment = Alignment.Center) { Text(row.glyph, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(row.sport, style = MaterialTheme.typography.titleMedium)
                Text(row.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.stats.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        row.stats.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ActivitiesPreview() = CairnTheme {
    val d = listOf(MetricUi("Sport", "Running"), MetricUi("Distance", "8.4 km"))
    ActivitiesContent(
        ActivitiesState(false, listOf(ActivityRowUi("1", "R", "Running", "Today", listOf("8.4 km", "46m", "5:28 /km", "151 bpm"), d, "Running")), FooterUi("Updated 1h ago", Freshness.Fresh)),
        onOpenAccount = {},
    )
}
