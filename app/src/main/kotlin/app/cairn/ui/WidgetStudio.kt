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

/**
 * Customise a widget (chosen on the Widgets home or from a card's long-press sheet), see it live with a live preview at its real size, then "Add to home screen". The choices are
 * stored in a pending-pin record and the launcher is asked for the provider variant whose default size matches, so the widget
 * lands already configured at that size.
 */
@Composable
fun WidgetStudioScreen(kind: WidgetKind, initialStyle: app.cairn.widget.WidgetStyle?, initialSize: WidgetSize?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val settings = graph.appSettings.widgetSettings

    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val display by graph.appSettings.display.collectAsStateWithLifecycle(initialValue = app.cairn.core.model.DisplayPrefs())
    val tapTarget by graph.appSettings.tapTarget(kind).collectAsStateWithLifecycle(initialValue = TapTarget.CorosHome)
    // The edits survive rotation, dark mode and font-scale changes: they are saved as the config's own lossless text form.
    var cfg by rememberSaveable(kind, initialStyle, stateSaver = WidgetConfigSaver(kind)) { mutableStateOf(WidgetConfig(style = initialStyle?.takeIf { it.kind == kind })) }
    var size by rememberSaveable(kind) { mutableStateOf((initialSize ?: WidgetVariants.suggested(kind)).name) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var editTap by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chosen = WidgetSize.parse(size) ?: WidgetSize.Wide
    val applier = remember { lookApplier(context, graph) }
    WidgetCustomizer(
        kind, cfg, { cfg = it }, appearance, chosen, { size = it.name },
        "Your launcher gets a ${WidgetVariants.pickerLabel(kind, chosen)} widget, so it lands at this size. You can still resize it later.",
        tapTarget, { editTap = true }, modifier.fillMaxSize(), appUnit = display.unit,
        applier = applier,
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

/** Saves a [WidgetConfig] through its own encode/decode (lossless and tolerant of unknown values), so an editor survives recreation. */
internal fun WidgetConfigSaver(kind: WidgetKind): androidx.compose.runtime.saveable.Saver<WidgetConfig, String> =
    androidx.compose.runtime.saveable.Saver(save = { it.encode() }, restore = { WidgetConfig.decode(it, kind) })

internal fun lookApplier(context: android.content.Context, graph: app.cairn.AppGraph): LookApplier<WidgetKind> {
    val settings = graph.appSettings.widgetSettings
    return LookApplier(
        ids = { placedWidgets(context) },
        load = { id, k -> settings.configNow(id, k) },
        save = { id, c -> settings.save(id, c) },
        refresh = { runCatching { app.cairn.widget.GlanceWidgetUpdater(context.applicationContext).updateAll() } },
        decode = { raw, k -> WidgetConfig.decode(raw, k) },
    )
}
