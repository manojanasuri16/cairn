package app.cairn.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cairn.CairnApp
import app.cairn.WidgetConfigActivity
import app.cairn.core.designsystem.Spacing
import app.cairn.widget.AppAppearance
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetPinner
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun widgetBlurb(k: WidgetKind) = when (k) {
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

/** The home screen: a gallery of every widget kind. Tapping a card opens its editor. */
@Composable
fun WidgetsHomeScreen(onAdd: (WidgetKind, WidgetSize) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val settings = graph.appSettings.widgetSettings
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val pinSupported = remember { WidgetPinner.isSupported(context) }

    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { UpdateBanner() }
        item { SyncStatusBar(null) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SectionTitle("Add a widget")
                Text(if (pinSupported) "Pick one, make it yours with a live preview, then add it to your home screen. Tip: press and hold any card in the app to add it too."
                    else "Your launcher cannot add widgets from an app. Pick one to design it, then long-press your home screen, choose Widgets and add it from there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(WidgetKind.entries.toList(), key = { it.name }) { k -> GalleryCard(k, appearance, onAdd) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryCard(kind: WidgetKind, appearance: AppAppearance, onAdd: (WidgetKind, WidgetSize) -> Unit) {
    var sizeName by rememberSaveable(kind) { mutableStateOf(WidgetVariants.suggested(kind).name) }
    val size = WidgetSize.parse(sizeName) ?: WidgetVariants.suggested(kind)
    val resolved = WidgetConfig().resolve(kind, appearance)
    Card(Modifier.fillMaxWidth(), colors = cairnCardColors()) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Customise ${kind.label} widget") { onAdd(kind, size) }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)).padding(Spacing.md), contentAlignment = Alignment.Center) {
                    WidgetThumbnail(kind, resolved, size, Modifier.fillMaxWidth())
                }
                Column(Modifier.padding(horizontal = Spacing.xs)) {
                    Text(kind.label, style = MaterialTheme.typography.titleMedium)
                    Text(widgetBlurb(kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(Modifier.padding(horizontal = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                WidgetSize.entries.forEach { s ->
                    FilterChip(selected = s == size, onClick = { sizeName = s.name }, label = { Text(s.cells) }, modifier = Modifier.semantics { role = Role.RadioButton })
                }
            }
        }
    }
}

/** Sub-screen from the Widgets menu: the default style, background and opacity for new widgets. */
@Composable
fun DefaultLookScreen(modifier: Modifier = Modifier) {
    val graph = (LocalContext.current.applicationContext as CairnApp).graph
    val settings = graph.appSettings.widgetSettings
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        WidgetAppearanceCard(
            appearance,
            onStyle = { scope.launch { settings.setTheme(it) } },
            onChange = { b, o -> scope.launch { settings.setDefaultBackground(b, o) } },
        )
    }
}
