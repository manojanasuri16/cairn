package app.cairn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cairn.CairnApp
import app.cairn.core.designsystem.Spacing
import app.cairn.widget.AppAppearance
import app.cairn.widget.TapTarget
import app.cairn.widget.WidgetCatalog
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetPinner
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetVariants
import kotlinx.coroutines.launch

private fun blurb(k: WidgetKind) = when (k) {
    WidgetKind.Today -> "Steps, sleep, resting heart rate and recovery"
    WidgetKind.Recovery -> "How recovered you are, and when you will be full"
    WidgetKind.LastActivity -> "Your latest workout at a glance"
    WidgetKind.TrainingLoad -> "A week of load, your ratio and VO2max"
    WidgetKind.Sleep -> "How long you slept, your score and stages"
    WidgetKind.Fitness -> "VO2max, threshold pace and race predictions"
    WidgetKind.Overview -> "Up to six of your stats in one grid"
    WidgetKind.Hrv -> "Last night's HRV, its assessment and normal range"
    WidgetKind.HeartRate -> "Resting heart rate, 7-day average, min and max"
    WidgetKind.Stress -> "Today's stress level and a week of bars"
    WidgetKind.WeeklyMileage -> "Distance this week, Monday to Sunday, and a goal ring"
}

/**
 * Add widget: choose a widget, customise it with a live preview at its real size, then "Add to home screen". The choices are
 * stored in a pending-pin record and the launcher is asked for the provider variant whose default size matches, so the widget
 * lands already configured at that size.
 */
@Composable
fun WidgetStudioScreen(initialKind: WidgetKind?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val settings = graph.appSettings.widgetSettings
    var kindName by rememberSaveable { mutableStateOf(initialKind?.name) }
    val kind = WidgetKind.parse(kindName)
    BackHandler(enabled = kind != null && initialKind == null) { kindName = null }
    if (kind == null) { Chooser(modifier) { kindName = it.name }; return }

    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val display by graph.appSettings.display.collectAsStateWithLifecycle(initialValue = app.cairn.core.model.DisplayPrefs())
    val tapTarget by graph.appSettings.tapTarget(kind).collectAsStateWithLifecycle(initialValue = TapTarget.CorosHome)
    var cfg by remember(kind) { mutableStateOf(WidgetConfig()) }
    var size by rememberSaveable(kind) { mutableStateOf(WidgetVariants.suggested(kind).name) }
    var message by remember { mutableStateOf<String?>(null) }
    var editTap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chosen = WidgetSize.parse(size) ?: WidgetSize.Wide
    WidgetCustomizer(
        kind, cfg, { cfg = it }, appearance, chosen, { size = it.name },
        "Your launcher gets a ${WidgetVariants.pickerLabel(kind, chosen)} widget, so it lands at this size. You can still resize it later.",
        tapTarget, { editTap = true }, modifier.fillMaxSize(), appUnit = display.unit,
        bottom = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        scope.launch {
                            // The same RemoteViews the preview shows, so the system dialog matches. Any failure pins without it.
                            val rv = runCatching {
                                kotlinx.coroutines.withTimeoutOrNull(1_500) { WidgetCatalog.pinPreviewViews(context, kind, cfg.resolve(kind, appearance), chosen) }
                            }.getOrNull()
                            val ok = runCatching { WidgetPinner.pin(context, kind, chosen, cfg, rv) }.getOrDefault(false)
                            message = if (ok) null else "This launcher cannot add widgets from an app. Long-press your home screen, choose Widgets and pick ${WidgetVariants.pickerLabel(kind, chosen)}."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(AppIcons.Add, null, Modifier.padding(end = Spacing.xs)); Text("Add to home screen") }
            }
        },
    )
    if (editTap) TapTargetPickerDialog(kind, tapTarget, onSave = { t -> scope.launch { graph.appSettings.setTapTarget(kind, t) }; editTap = false }, onDismiss = { editTap = false })
}

@Composable
private fun Chooser(modifier: Modifier, onPick: (WidgetKind) -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Choose a widget", style = MaterialTheme.typography.titleMedium)
        Text("You will pick its size and look next, with a live preview.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WidgetKind.entries.forEach { k ->
            Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(k) }, colors = cairnCardColors()) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(AppIcons.Widgets, null, Modifier.padding(2.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(k.label, style = MaterialTheme.typography.titleMedium)
                        Text(blurb(k), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(AppIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
