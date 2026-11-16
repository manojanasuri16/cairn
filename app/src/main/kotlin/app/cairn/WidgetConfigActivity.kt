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
import app.cairn.ui.WidgetCustomizer
import androidx.compose.material3.OutlinedButton
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
        // Pinned from the in-app widget studio: the choices were already made, so apply them and step aside.
        val pins = app.cairn.widget.PendingPins(app.cairn.widget.SharedPrefsPinKv(this))
        val nowMs = System.currentTimeMillis()
        val providerSize = app.cairn.widget.WidgetVariants.sizeOf(AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className)
        if (pins.wasClaimedRecently(id, nowMs)) { setResult(RESULT_OK, result); finish(); return }
        pins.claim(kind, nowMs, providerSize)?.let { pin ->
            lifecycleScope.launch {
                graph.appSettings.widgetSettings.save(id, pin.config)
                graph.appSettings.widgetSettings.markConfigShown(id)
                pins.markClaimed(id, nowMs)
                runCatching { WidgetCatalog.updateOne(applicationContext, kind, id) }
                setResult(RESULT_OK, result)
                finish()
            }
            return
        }
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

@Composable
private fun ConfigScreen(graph: AppGraph, kind: WidgetKind, id: Int, onDone: (WidgetConfig) -> Unit, onCancel: () -> Unit) {
    val settings = graph.appSettings.widgetSettings
    val context = androidx.compose.ui.platform.LocalContext.current
    var loaded by remember { mutableStateOf<WidgetConfig?>(null) }
    LaunchedEffect(id) {
        loaded = settings.configNow(id, kind)
        app.cairn.widget.WidgetLog.d("config loaded kind=$kind id=$id key=wcfg_$id metrics=${loaded?.metrics?.map { it.name }} style=${loaded?.style}")
    }
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val display by graph.appSettings.display.collectAsStateWithLifecycle(initialValue = app.cairn.core.model.DisplayPrefs())
    val tapTarget by graph.appSettings.tapTarget(kind).collectAsStateWithLifecycle(initialValue = TapTarget.CorosHome)
    val scope = rememberCoroutineScope()
    val cfg = loaded
    var editTap by remember { mutableStateOf(false) }
    // The preview starts at the size this widget was placed at; the size section only changes the preview here.
    val placed = remember(id) { app.cairn.widget.WidgetVariants.sizeOf(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className) ?: app.cairn.widget.WidgetVariants.suggested(kind) }
    var size by remember { mutableStateOf(placed) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (cfg == null) return@Surface
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("${kind.label} widget", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
            WidgetCustomizer(
                kind, cfg, { loaded = it }, appearance, size, { size = it },
                "This only changes the preview. Resize the widget on your home screen by long-pressing it.",
                tapTarget, { editTap = true }, Modifier.weight(1f), appUnit = display.unit,
                bottom = {
                    Row(Modifier.fillMaxWidth().padding(Spacing.md).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { onDone(cfg) }, modifier = Modifier.weight(1f)) { Text("Done") }
                    }
                },
            )
        }
        if (editTap) TapTargetPickerDialog(kind, tapTarget, onSave = { t -> scope.launch { graph.appSettings.setTapTarget(kind, t) }; editTap = false }, onDismiss = { editTap = false })
    }
}
