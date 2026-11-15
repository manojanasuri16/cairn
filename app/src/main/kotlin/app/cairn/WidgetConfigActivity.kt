package app.cairn

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.designsystem.ThemeId
import app.cairn.ui.SectionCard
import app.cairn.ui.SectionTitle
import app.cairn.ui.TapTargetPickerDialog
import app.cairn.ui.ThemeSwatch
import app.cairn.ui.describe
import app.cairn.widget.AppAppearance
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.GlanceWidgetUpdater
import app.cairn.widget.OverviewId
import app.cairn.widget.Part
import app.cairn.widget.TapTarget
import app.cairn.widget.UnitsPref
import app.cairn.widget.WidgetCatalog
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetStyle
import kotlinx.coroutines.launch

/**
 * Per-widget configuration: opened by the launcher when placing a widget and again from "Reconfigure". A live preview
 * (the real Glance widget, rendered from synthetic data) sits above the options and updates as you change them.
 */
class WidgetConfigActivity : ComponentActivity() {
    /** Started a second time for the same widget (launcher and pin callback both asked): keep the one on screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Stays RESULT_CANCELED until Done: when the launcher started this screen, backing out or Cancel adds no widget. When it came from our own pin
        // callback (EXTRA_FROM_PIN) the widget is already placed, so cancelling just leaves it with its defaults.
        setResult(RESULT_CANCELED, result)
        val kind = WidgetCatalog.kindOf(AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) { finish(); return }
        val graph = (application as CairnApp).graph
        // Remember that the screen was shown, so a pin callback never opens a second copy.
        lifecycleScope.launch { graph.appSettings.widgetSettings.markConfigShown(id) }
        setContent {
            val look by graph.appSettings.look.collectAsStateWithLifecycle(initialValue = app.cairn.core.designsystem.AppLook())
            CairnTheme(look) {
                ConfigScreen(
                    graph, kind, id,
                    onDone = { cfg ->
                        lifecycleScope.launch {
                            app.cairn.widget.WidgetLog.d("config Done kind=$kind id=$id fromPin=${intent?.getBooleanExtra(app.cairn.widget.EXTRA_FROM_PIN, false)} saving metrics=${cfg.metrics?.map { it.name }} style=${cfg.style} theme=${cfg.theme}")
            graph.appSettings.widgetSettings.save(id, cfg)
                            // Redraw this widget right away (its saved choices are already written), then any others of the kind.
                            app.cairn.widget.WidgetLog.d("config updateOne id=$id then update(kind=$kind)")
                            runCatching { WidgetCatalog.updateOne(applicationContext, kind, id) }
                            runCatching { GlanceWidgetUpdater(applicationContext).update(kind) }
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(graph: AppGraph, kind: WidgetKind, id: Int, onDone: (WidgetConfig) -> Unit, onCancel: () -> Unit) {
    val settings = graph.appSettings.widgetSettings
    var loaded by remember { mutableStateOf<WidgetConfig?>(null) }
    LaunchedEffect(id) {
        loaded = settings.configNow(id, kind)
        app.cairn.widget.WidgetLog.d("config loaded kind=$kind id=$id key=wcfg_$id metrics=${loaded?.metrics?.map { it.name }} style=${loaded?.style}")
    }
    val app by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val tapTarget by graph.appSettings.tapTarget(kind).collectAsStateWithLifecycle(initialValue = TapTarget.CorosHome)
    val scope = rememberCoroutineScope()
    val cfg = loaded
    var editTap by remember { mutableStateOf(false) }
    var sizeIdx by remember { mutableStateOf(-1) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (cfg == null) return@Surface
        fun set(c: WidgetConfig) { loaded = c }
        val resolved = cfg.resolve(kind, app)
        val sizes = listOf("Small" to DpSize(160.dp, 160.dp), "Wide" to DpSize(300.dp, 150.dp), "Large" to DpSize(300.dp, 300.dp))
        val defaultIdx = sizes.indexOfFirst { it.second == WidgetCatalog.previewSize(kind) }.coerceAtLeast(0)
        val size = sizes[if (sizeIdx < 0) defaultIdx else sizeIdx]
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("${kind.label} widget", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
            // Live preview on a neutral "wallpaper" so transparency is visible.
            Box(Modifier.fillMaxWidth().background(Color(0xFF6B7280)).padding(Spacing.sm), contentAlignment = Alignment.Center) {
                LivePreview(kind, resolved, size.second)
            }
            FlowRow(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                sizes.forEachIndexed { i, (label, _) ->
                    FilterChip(selected = size.first == label, onClick = { sizeIdx = i }, label = { Text("Preview: $label") }, modifier = Modifier.semantics { role = Role.RadioButton })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (kind == WidgetKind.Overview) SectionCard {
                    val picked = resolved.metrics
                    SectionTitle("Stats (1 to 6, in this order)")
                    picked.forEachIndexed { i, id ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${id.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            androidx.compose.material3.TextButton(enabled = i > 0, onClick = { set(cfg.copy(metrics = picked.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })) }, modifier = Modifier.semantics { contentDescription = "Move ${id.label} up" }) { Text("\u25B2") }
                            androidx.compose.material3.TextButton(enabled = i < picked.lastIndex, onClick = { set(cfg.copy(metrics = picked.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })) }, modifier = Modifier.semantics { contentDescription = "Move ${id.label} down" }) { Text("\u25BC") }
                            androidx.compose.material3.TextButton(enabled = picked.size > 1, onClick = { set(cfg.copy(metrics = picked - id)) }, modifier = Modifier.semantics { contentDescription = "Remove ${id.label}" }) { Text("Remove") }
                        }
                    }
                    Text(if (picked.size >= OverviewId.MAX) "Six stats is the most." else "Add a stat", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        OverviewId.entries.filter { it !in picked }.forEach { id ->
                            FilterChip(selected = false, enabled = picked.size < OverviewId.MAX, onClick = { set(cfg.copy(metrics = picked + id)) }, label = { Text(id.label) })
                        }
                    }
                }
                SectionCard {
                    SectionTitle("Style")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        WidgetStyle.forKind(kind).forEach { s ->
                            FilterChip(selected = resolved.style == s, onClick = { set(cfg.copy(style = s)) }, label = { Text(s.label) }, modifier = Modifier.semantics { role = Role.RadioButton })
                        }
                    }
                }
                SectionCard {
                    SectionTitle("Style")
                    FilterChip(selected = cfg.theme == null, onClick = { set(cfg.copy(theme = null)) }, label = { Text("App default (${app.theme.label})") }, modifier = Modifier.semantics { role = Role.RadioButton })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ThemeId.entries.forEach { t -> ThemeSwatch(t, cfg.theme == t) { set(cfg.copy(theme = t)) } }
                    }
                    SectionTitle("Accent")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        val dark = isSystemInDarkTheme()
                        AccentOption.entries.forEach { a ->
                            FilterChip(
                                selected = cfg.accent == a, onClick = { set(cfg.copy(accent = a)) },
                                label = { Text(a.label) },
                                leadingIcon = { if (a != AccentOption.Theme) Box(Modifier.size(14.dp).background(Color(if (dark) a.dark else a.light), CircleShape)) },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                            )
                        }
                    }
                }
                SectionCard {
                    SectionTitle("Background")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FilterChip(selected = cfg.bg == null, onClick = { set(cfg.copy(bg = null, opacity = null)) }, label = { Text("App default (${app.bg.label})") })
                        Bg.entries.forEach { b -> FilterChip(selected = cfg.bg == b, onClick = { set(cfg.copy(bg = b)) }, label = { Text(b.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
                    }
                    if (resolved.bg != Bg.Transparent) {
                        Text("Opacity ${resolved.opacity}%", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = resolved.opacity.toFloat(), onValueChange = { set(cfg.copy(opacity = it.toInt())) }, valueRange = 0f..100f,
                            modifier = Modifier.semantics { contentDescription = "Opacity"; stateDescription = "${resolved.opacity} percent" },
                        )
                    }
                    SectionTitle("Corners")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Corners.entries.forEach { c -> FilterChip(selected = cfg.corners == c, onClick = { set(cfg.copy(corners = c)) }, label = { Text(c.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
                    }
                }
                SectionCard {
                    SectionTitle("Show")
                    Part.forKind(kind).forEach { part ->
                        val on = cfg.shows(part)
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { set(cfg.copy(hidden = if (on) cfg.hidden + part else cfg.hidden - part)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = Spacing.sm))
                            Text(part.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    SectionTitle("Footer")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FilterChip(selected = !cfg.compactFooter, onClick = { set(cfg.copy(compactFooter = false)) }, label = { Text("Full") }, modifier = Modifier.semantics { role = Role.RadioButton })
                        FilterChip(selected = cfg.compactFooter, onClick = { set(cfg.copy(compactFooter = true)) }, label = { Text("Compact") }, modifier = Modifier.semantics { role = Role.RadioButton })
                    }
                    Text("The data age is always shown, so you can tell when the numbers are old.", style = MaterialTheme.typography.bodySmall)
                }
                if (kind == WidgetKind.LastActivity || kind == WidgetKind.Fitness) SectionCard {
                    SectionTitle("Units")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        UnitsPref.entries.forEach { u -> FilterChip(selected = cfg.units == u, onClick = { set(cfg.copy(units = u)) }, label = { Text(u.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
                    }
                }
                SectionCard {
                    SectionTitle("When tapped")
                    Text(tapTarget.describe(), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { editTap = true }) { Text("Change tap target") }
                    Text("This applies to every ${kind.label} widget.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.md).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onDone(cfg) }, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
        if (editTap) TapTargetPickerDialog(kind, tapTarget, onSave = { t -> scope.launch { graph.appSettings.setTapTarget(kind, t) }; editTap = false }, onDismiss = { editTap = false })
    }
}

/** Renders the actual widget (Glance RemoteViews) at [size]; re-renders whenever the choices change. */
@Composable
private fun LivePreview(kind: WidgetKind, resolved: app.cairn.widget.ResolvedConfig, size: DpSize) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
    LaunchedEffect(resolved, size) { views = runCatching { WidgetCatalog.previewViews(context, kind, resolved, size) }.getOrNull() }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            views?.let { v -> frame.removeAllViews(); runCatching { frame.addView(v.apply(context.applicationContext, frame)) } }
        },
        modifier = Modifier.size(size.width, size.height).semantics { contentDescription = "Live preview of the ${kind.label} widget" },
    )
}
