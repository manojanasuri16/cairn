package app.cairn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cairn.CorosLauncher
import app.cairn.core.designsystem.Spacing
import app.cairn.widget.CorosScreenOption
import app.cairn.widget.CorosScreens
import app.cairn.widget.LaunchStep
import app.cairn.widget.TapTarget
import app.cairn.widget.WidgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain-words name of a target for the settings rows. */
fun TapTarget.describe(): String = when (this) {
    TapTarget.CorosHome -> "COROS home"
    TapTarget.OpenCairn -> "Open Cairn"
    is TapTarget.CorosScreen -> className.substringAfterLast('.')
}

@Composable
fun TapTargetsCard(targets: Map<WidgetKind, TapTarget>, onEdit: (WidgetKind) -> Unit) {
    SectionCard {
        SectionTitle("Widget tap targets")
        Text(
            "Choose what opens when you tap each widget. Only COROS screens that COROS allows other apps to open are listed, " +
                "and some may need information Cairn can't supply, so they may show an error or a blank page.",
            style = MaterialTheme.typography.bodySmall,
        )
        WidgetKind.entries.forEach { kind ->
            val current = targets[kind] ?: TapTarget.CorosHome
            OutlinedButton(onClick = { onEdit(kind) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(current.describe(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun TapTargetPickerDialog(kind: WidgetKind, current: TapTarget, onSave: (TapTarget) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val launcher = remember { CorosLauncher(context) }
    var choice by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }
    var testNote by remember { mutableStateOf<String?>(null) }
    val screens by produceState<List<CorosScreenOption>?>(null) { value = withContext(Dispatchers.Default) { launcher.discover() } }
    val shown = screens?.let { CorosScreens.filter(it, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${kind.label} widget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    "Only screens COROS allows other apps to open are listed. Some may need data Cairn can't supply.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search screens") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    item { OptionRow("COROS home", "Opens the COROS app", choice == TapTarget.CorosHome) { choice = TapTarget.CorosHome } }
                    item { OptionRow("Open Cairn", "Opens the matching Cairn screen", choice == TapTarget.OpenCairn) { choice = TapTarget.OpenCairn } }
                    when {
                        shown == null -> item { Text("Looking for COROS screens...", Modifier.padding(vertical = Spacing.sm), style = MaterialTheme.typography.bodySmall) }
                        shown.isEmpty() -> item {
                            Text(
                                if (screens.isNullOrEmpty()) "No openable COROS screens found. COROS may not be installed." else "No screens match your search.",
                                Modifier.padding(vertical = Spacing.sm), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        else -> items(shown, key = { it.className }) { o ->
                            OptionRow(o.title, o.className, (choice as? TapTarget.CorosScreen)?.className == o.className) { choice = TapTarget.CorosScreen(o.className) }
                        }
                    }
                }
                testNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(choice) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val worked = launcher.launch(choice, kind.cairnScreen, scheduleRefresh = false)
                    testNote = TapTargetTest.message(choice, worked)
                }) { Text("Test") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** What to tell the user after a Test tap. Pure, so it is unit-tested. */
object TapTargetTest {
    fun message(chosen: TapTarget, worked: LaunchStep?): String? = when {
        worked == null -> "Nothing could be opened."
        chosen is TapTarget.CorosScreen && worked !is LaunchStep.Screen -> "That screen would not open, so COROS home was used instead."
        chosen is TapTarget.CorosHome && worked == LaunchStep.Explain -> "COROS could not be opened, so Cairn showed help instead."
        chosen is TapTarget.CorosScreen && worked == LaunchStep.Explain -> "COROS could not be opened, so Cairn showed help instead."
        else -> null
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
