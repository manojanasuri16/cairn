package app.cairn.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.CairnApp
import app.cairn.CorosViewModel
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.source.healthconnect.HealthConnectReader
import app.cairn.source.healthconnect.syncReadPermissions
import app.cairn.BuildConfig
import app.cairn.ExplorerFeature
import app.cairn.sync.NudgeSettings
import app.cairn.sync.SyncSettings
import app.cairn.core.designsystem.AppLook
import app.cairn.core.designsystem.ThemeId
import app.cairn.widget.AppAppearance
import app.cairn.widget.Bg
import app.cairn.widget.PinnableWidget
import app.cairn.widget.TapTarget
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetPinner
import kotlinx.coroutines.launch

class SettingsNav(
    val onInspector: () -> Unit,
    val onDiagnostics: () -> Unit,
    val onFreshness: () -> Unit,
    val onAccount: () -> Unit,
    val onExplorer: () -> Unit = {},
)

@Composable
fun SettingsScreen(coros: CorosViewModel, nav: SettingsNav, modifier: Modifier = Modifier, vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val corosState by coros.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val graph = (context.applicationContext as CairnApp).graph
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSources() }
    val grant = rememberLauncherForActivityResult(HealthConnectReader.permissionContract()) { vm.onPermissionResult() }

    val nudgeOn by graph.nudgeSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val nudgeHours by graph.nudgeSettings.thresholdHours.collectAsStateWithLifecycle(initialValue = NudgeSettings.DEFAULT)
    var notifDenied by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifDenied = !ok
        scope.launch { graph.nudgeSettings.setEnabled(ok) }
    }
    val look by graph.appSettings.look.collectAsStateWithLifecycle(initialValue = AppLook())
    val appearance by graph.appSettings.widgetSettings.appearance.collectAsStateWithLifecycle(initialValue = AppAppearance())
    val tapTargets by graph.appSettings.tapTargets.collectAsStateWithLifecycle(initialValue = emptyMap())
    var editingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) view.confirmHaptic() }

    SettingsContent(
        state = state,
        corosSignedIn = corosState.signedIn || state.corosSignedIn,
        corosStatus = corosState.status,
        corosBusy = corosState.busy,
        signInHint = corosState.signInHint,
        nudgeOn = nudgeOn,
        nudgeHours = nudgeHours,
        notifDenied = notifDenied,
        widgetsSupported = remember { WidgetPinner.isSupported(context) },
        onGrantHc = { grant.launch(syncReadPermissions) },
        onCorosSignIn = { coros.signIn(context) },
        onCorosSignOut = { coros.signOut(); vm.refreshSources() },
        onInterval = vm::setInterval,
        onFresh = vm::setFresh,
        onAging = vm::setAging,
        onUnit = vm::setUnit,
        onNudge = { want ->
            if (!want) scope.launch { graph.nudgeSettings.setEnabled(false); graph.notifier.cancel() }
            else if (graph.notifier.hasPermission()) { notifDenied = false; scope.launch { graph.nudgeSettings.setEnabled(true) } }
            else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onNudgeHours = { scope.launch { graph.nudgeSettings.setThreshold(it) } },
        onPin = { WidgetPinner.pin(context, it) },
        tapTargets = tapTargets,
        onEditTapTarget = { editingTarget = it.name },
        nav = nav,
        onDeleteRequest = { confirmDelete = true },
        modifier = modifier,
        look = look,
        onLook = { scope.launch { graph.appSettings.setLook(it) } },
        onWidgetStyle = { scope.launch { graph.appSettings.widgetSettings.setTheme(it) } },
        appearance = appearance,
        onAppearance = { b, o -> scope.launch { graph.appSettings.widgetSettings.setDefaultBackground(b, o) } },
    )
    WidgetKind.parse(editingTarget)?.let { kind ->
        TapTargetPickerDialog(
            kind = kind,
            current = tapTargets[kind] ?: TapTarget.CorosHome,
            onSave = { t -> scope.launch { graph.appSettings.setTapTarget(kind, t) }; editingTarget = null },
            onDismiss = { editingTarget = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all Cairn data?") },
            text = {
                Text(
                    "This removes every health value Cairn has stored on this phone (steps, sleep, recovery, training load, fitness and activities) and its sync history. " +
                        "Your data in COROS and Health Connect is not touched, and you stay signed in. Cairn fills the cache again on the next refresh.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll() }) { Text("Delete all data") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep data") } },
        )
    }
    if (state.deleted) {
        AlertDialog(
            onDismissRequest = vm::clearDeletedNotice,
            title = { Text("Data deleted") },
            text = { Text("Cairn's stored data was deleted from this phone.") },
            confirmButton = { TextButton(onClick = vm::clearDeletedNotice) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsState,
    corosSignedIn: Boolean,
    corosStatus: String,
    corosBusy: Boolean,
    signInHint: String?,
    nudgeOn: Boolean,
    nudgeHours: Int,
    notifDenied: Boolean,
    widgetsSupported: Boolean,
    onGrantHc: () -> Unit,
    onCorosSignIn: () -> Unit,
    onCorosSignOut: () -> Unit,
    onInterval: (Int) -> Unit,
    onFresh: (Int) -> Unit,
    onAging: (Int) -> Unit,
    onUnit: (DistanceUnit) -> Unit,
    onNudge: (Boolean) -> Unit,
    onNudgeHours: (Int) -> Unit,
    onPin: (PinnableWidget) -> Unit,
    tapTargets: Map<WidgetKind, TapTarget>,
    onEditTapTarget: (WidgetKind) -> Unit,
    nav: SettingsNav,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    look: AppLook = AppLook(),
    onLook: (AppLook) -> Unit = {},
    onWidgetStyle: (ThemeId) -> Unit = {},
    appearance: AppAppearance = AppAppearance(),
    onAppearance: (Bg, Int) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ThemeCard(look, onLook)
        WidgetAppearanceCard(appearance, onWidgetStyle, onAppearance)

        SectionCard {
            SectionTitle("Sources")
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Text(SourceStatus.label(state.hc), style = MaterialTheme.typography.bodyMedium)
            if (SourceStatus.canGrant(state.hc)) Button(onClick = onGrantHc) { Text("Grant Health Connect access") }
            Text("COROS account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xs))
            Text(if (corosSignedIn) "Signed in" else corosStatus.ifBlank { "Not signed in" }, style = MaterialTheme.typography.bodyMedium)
            signInHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            if (!corosSignedIn) Button(onClick = onCorosSignIn, enabled = !corosBusy) { Text("Sign in to COROS") }
        }

        SectionCard {
            SectionTitle("Refresh")
            Text("Refresh interval", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(SyncSettings.CHOICES, state.intervalMinutes, { "$it min" }, onInterval)
            Text("Mark data as getting old after", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(DisplayPrefs.FRESH_CHOICES, state.display.freshHours, { "$it h" }, onFresh)
            Text("Mark data as stale after", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(DisplayPrefs.AGING_CHOICES, state.display.agingHours, { "$it h" }, onAging)
            Text("These tiers also drive the age line on widgets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard {
            SectionTitle("Reminder")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Remind me when COROS hasn't synced", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = nudgeOn, onCheckedChange = onNudge)
            }
            Text(
                "Cairn shows at most one quiet notification per day, between 08:00 and 22:00, when the newest data is older than the threshold. Tapping it opens COROS so it can sync. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (notifDenied) Text("Notifications are blocked for Cairn, so the reminder stays off. Allow them in system settings to use it.", color = MaterialTheme.colorScheme.error)
            Text("Remind me after", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(NudgeSettings.CHOICES, nudgeHours, { "$it h" }, onNudgeHours, enabled = nudgeOn)
        }

        SectionCard {
            SectionTitle("Units")
            Text("Distance and pace", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(DistanceUnit.entries, state.display.unit, { if (it == DistanceUnit.Km) "Kilometres" else "Miles" }, onUnit)
        }

        if (widgetsSupported) {
            SectionCard {
                SectionTitle("Add a widget")
                Text("Pick a style and colours when you place a widget; long-press it later to reconfigure.", style = MaterialTheme.typography.bodySmall)
                PinnableWidget.entries.forEach { w ->
                    OutlinedButton(onClick = { onPin(w) }, modifier = Modifier.fillMaxWidth()) { Text("Add ${w.label} widget") }
                    Text(w.styles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        TapTargetsCard(tapTargets, onEditTapTarget)

        SectionCard {
            SectionTitle("Tools")
            LinkRow("Data sources inspector", nav.onInspector)
            LinkRow("Sync diagnostics", nav.onDiagnostics)
            if (BuildConfig.DEBUG && ExplorerFeature.available) LinkRow("Tool explorer (debug)", nav.onExplorer)
            LinkRow("Keep data fresh", nav.onFreshness)
            LinkRow("COROS account", nav.onAccount)
        }

        SectionCard {
            SectionTitle("Your data")
            OutlinedButton(onClick = onCorosSignOut, enabled = corosSignedIn && !corosBusy, modifier = Modifier.fillMaxWidth()) { Text("Sign out of COROS") }
            OutlinedButton(
                onClick = onDeleteRequest, enabled = !state.deleting, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.deleting) "Deleting..." else "Delete all Cairn data") }
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        choices.forEach { c ->
            FilterChip(selected = selected == c, enabled = enabled, onClick = { onSelect(c) }, label = { Text(label(c)) }, modifier = Modifier.semantics { role = Role.RadioButton })
        }
    }
}
