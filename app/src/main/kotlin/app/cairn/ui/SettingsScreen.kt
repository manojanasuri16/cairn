package app.cairn.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import app.cairn.core.designsystem.ThemeMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
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
import app.cairn.DemoFeature
import app.cairn.ExplorerFeature
import app.cairn.appdrop.AppDropUpdater
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
    val onTools: () -> Unit = {},
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
        nav = nav,
        onDeleteRequest = { confirmDelete = true },
        modifier = modifier,
        look = look,
        onLook = { scope.launch { graph.appSettings.setLook(it) } },
    )
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
    onGrantHc: () -> Unit,
    onCorosSignIn: () -> Unit,
    onCorosSignOut: () -> Unit,
    onInterval: (Int) -> Unit,
    onFresh: (Int) -> Unit,
    onAging: (Int) -> Unit,
    onUnit: (DistanceUnit) -> Unit,
    onNudge: (Boolean) -> Unit,
    onNudgeHours: (Int) -> Unit,
    nav: SettingsNav,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    look: AppLook = AppLook(),
    onLook: (AppLook) -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SettingsGroup(AppIcons.Palette, "Appearance", "Colours and light or dark mode for the app.") {
            AccentPicker(look, onLook)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = look.mode == m, onClick = { onLook(look.copy(mode = m)) }, label = { Text(m.label) }, modifier = Modifier.semantics { role = Role.RadioButton })
                }
                FilterChip(selected = look.dynamic, onClick = { onLook(look.copy(dynamic = !look.dynamic)) }, label = { Text("Material You colours") })
            }
        }

        SettingsGroup(AppIcons.Sync, "Sync", "How often Cairn checks COROS, and when data counts as old.") {
            Text("Refresh every", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(SyncSettings.CHOICES, state.intervalMinutes, { "$it min" }, onInterval)
            if (state.intervalMinutes == SyncSettings.FAST) {
                Text(
                    "Every 5 minutes uses more battery and COROS requests, and Android may delay it. Cairn keeps those runs light (recovery and daily numbers only).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("You can always sync right away with the sync button. 30 minutes is a good balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Getting old after", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(DisplayPrefs.FRESH_CHOICES, state.display.freshHours, { "$it h" }, onFresh)
            Text("Stale after", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(DisplayPrefs.AGING_CHOICES, state.display.agingHours, { "$it h" }, onAging)
            Text("The dot beside the sync button turns amber, then red, at these ages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsGroup(AppIcons.Link, "Sources", "Where your numbers come from.") {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Text(SourceStatus.label(state.hc), style = MaterialTheme.typography.bodyMedium)
            if (SourceStatus.canGrant(state.hc)) Button(onClick = onGrantHc) { Text("Grant Health Connect access") }
            LinkRow(
                "COROS account",
                if (corosSignedIn) "Signed in. Manage sign-in, diagnostics or sign in again" else corosStatus.ifBlank { "Not signed in" } + ". Tap to sign in",
                nav.onAccount,
            )
            signInHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            if (!corosSignedIn) Button(onClick = onCorosSignIn, enabled = !corosBusy) { Text("Sign in to COROS") }
        }

        SettingsGroup(AppIcons.Bell, "Notifications", "An optional quiet reminder when COROS has not synced.") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Remind me", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = nudgeOn, onCheckedChange = onNudge)
            }
            Text("At most one a day, between 08:00 and 22:00. Tapping it opens COROS so it can sync.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notifDenied) Text("Notifications are blocked for Cairn, so the reminder stays off. Allow them in system settings to use it.", color = MaterialTheme.colorScheme.error)
            if (nudgeOn) { Text("After", style = MaterialTheme.typography.titleMedium); ChoiceRow(NudgeSettings.CHOICES, nudgeHours, { "$it h" }, onNudgeHours) }
        }

        SettingsGroup(AppIcons.Ruler, "Units", "Distance and pace.") {
            ChoiceRow(DistanceUnit.entries, state.display.unit, { if (it == DistanceUnit.Km) "Kilometres" else "Miles" }, onUnit)
        }

        SettingsGroup(AppIcons.Build, "Tools", "Diagnostics, data sources and tips.") {
            LinkRow("Open tools", "Sync diagnostics, data sources, keep data fresh", nav.onTools)
        }

        AboutGroup()

        SettingsGroup(AppIcons.Delete, "Your data", "Everything stays on this phone.") {
            OutlinedButton(onClick = onCorosSignOut, enabled = corosSignedIn && !corosBusy, modifier = Modifier.fillMaxWidth()) { Text("Sign out of COROS") }
            OutlinedButton(
                onClick = onDeleteRequest, enabled = !state.deleting, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.deleting) "Deleting..." else "Delete all Cairn data") }
        }
    }
}

/** A settings card with an icon, a title and a one-line description. */
@Composable
private fun SettingsGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, content: @Composable () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Sub-screen: what each widget opens when tapped. */
@Composable
fun TapTargetsScreen(modifier: Modifier = Modifier) {
    val graph = (LocalContext.current.applicationContext as CairnApp).graph
    val tapTargets by graph.appSettings.tapTargets.collectAsStateWithLifecycle(initialValue = emptyMap())
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) { TapTargetsCard(tapTargets) { editing = it.name } }
    WidgetKind.parse(editing)?.let { kind ->
        TapTargetPickerDialog(kind, tapTargets[kind] ?: TapTarget.CorosHome, onSave = { t -> scope.launch { graph.appSettings.setTapTarget(kind, t) }; editing = null }, onDismiss = { editing = null })
    }
}

/** Sub-screen: diagnostics and helpers, out of the main settings list. */
@Composable
fun ToolsScreen(nav: SettingsNav, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionCard {
            LinkRow("Sync diagnostics", "Last runs, scheduler state, request count", nav.onDiagnostics)
            LinkRow("Data sources inspector", "What Cairn can read from Health Connect", nav.onInspector)
            LinkRow("Keep data fresh", "Open COROS and battery tips", nav.onFreshness)
            LinkRow("COROS account", "Sign in or out", nav.onAccount)
            if (BuildConfig.DEBUG && ExplorerFeature.available) LinkRow("Explorer (debug)", "Call COROS tools by hand", nav.onExplorer)
            if (BuildConfig.DEBUG && DemoFeature.available) DemoRow()
        }
    }
}

/** Debug builds only: fictional data for screenshots; sync is paused while on. */
@Composable
private fun DemoRow() {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val on by DemoFeature.flow.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.weight(1f)) {
            Text("Demo mode (debug)", style = MaterialTheme.typography.titleMedium)
            Text("Fictional data for screenshots. Sync is paused; turning it off clears it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = { DemoFeature.set(graph, context.applicationContext, it) })
    }
}

/** Version, update check, feedback and a plain statement of what the app sends. */
@Composable
private fun AboutGroup() {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
    SettingsGroup(AppIcons.Link, "About", "Cairn ${BuildConfig.VERSION_NAME}") {
        Text(
            "Cairn contacts the AppDrop store only to check for updates and to send feedback you choose to send. No health data is ever sent.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { activity?.let { AppDropUpdater.check(it, force = true) } }, enabled = activity != null, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
        OutlinedButton(onClick = { activity?.let { AppDropUpdater.openFeedback(it) } }, enabled = activity != null, modifier = Modifier.fillMaxWidth()) { Text("Send feedback") }
    }
}

@Composable
private fun LinkRow(label: String, subtitle: String, onClick: () -> Unit) {
    ActionRow(AppIcons.ChevronRight, label, subtitle, onClick)
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
