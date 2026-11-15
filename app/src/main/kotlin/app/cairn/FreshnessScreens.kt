package app.cairn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cairn.sync.BatteryCheck
import app.cairn.sync.ManualSync
import app.cairn.sync.NudgeSettings
import app.cairn.sync.SyncReason
import app.cairn.sync.SyncScheduler
import app.cairn.sync.SyncState
import app.cairn.widget.COROS_PACKAGE
import java.time.Instant
import kotlinx.coroutines.launch

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/** Sync-now button with immediate feedback: skipped reason, "Syncing...", then the result. Used on every screen with the button. */
@Composable
fun SyncNowButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val state by graph.syncState.state.collectAsState(initial = SyncState())
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_NAME) }
        .collectAsState(initial = emptyList<WorkInfo>())
    var message by remember { mutableStateOf<String?>(null) }
    var startedMs by remember { mutableStateOf<Long?>(null) }
    val active = work.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    var syncing by remember { mutableStateOf(false) }
    val signedIn = remember(state) { graph.tokenStore.load() != null }

    // Show the result once the requested run has finished and recorded itself.
    LaunchedEffect(active, state.runs.firstOrNull()?.atMs) {
        val started = startedMs ?: return@LaunchedEffect
        if (syncing && !active) {
            ManualSync.result(state, started)?.let { message = it; syncing = false; startedMs = null }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            enabled = !syncing,
            onClick = {
                val now = Instant.now()
                when (val g = ManualSync.gate(state, now, signedIn, isOnline(context))) {
                    is ManualSync.Gate.Skipped -> message = g.message
                    ManualSync.Gate.Allowed -> {
                        startedMs = now.toEpochMilli(); syncing = true; message = null
                        graph.syncScheduler.requestNow(SyncReason.MANUAL)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (syncing) "Syncing..." else "Sync now") }
        (if (syncing) "Syncing..." else message)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun corosIgnoring(context: Context): Boolean? =
    runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(COROS_PACKAGE) }.getOrNull()

@Composable
fun FreshnessScreen(corosMissing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val installed = remember { isCorosInstalled(context) }
    val corosIgnoringOpt = remember { if (installed) corosIgnoring(context) else null }
    val cairnIgnoring = remember {
        runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
    }
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (corosMissing || !installed) {
            Text("The COROS app is not installed on this phone. Cairn can only show data COROS has synced, so install COROS and sign in there.", color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Cairn can only be as fresh as COROS's last sync. COROS syncs your watch only while its app is running, and Cairn cannot force that. " +
                "Opening COROS lets it sync, and Cairn then refreshes about 90 seconds and 5 minutes later.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { openCorosAndRefresh(context) }, modifier = Modifier.fillMaxWidth()) { Text("Open COROS") }
        SyncNowButton()

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("COROS background check", style = MaterialTheme.typography.titleSmall)
                Text("Installed: ${if (installed) "yes" else "no"}")
                Text("Battery: ${if (installed) BatteryCheck.label(corosIgnoringOpt) else "n/a"}")
                OutlinedButton(
                    enabled = installed,
                    onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$COROS_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                ) { Text("Open COROS app info") }
                Text("Tips for COROS: allow background activity, set battery to Unrestricted (or turn battery optimisation off), and do not swipe it away from recents.", style = MaterialTheme.typography.bodySmall)
                Text("Xiaomi/MIUI: Autostart on, Battery saver set to No restrictions, lock the app in recents.", style = MaterialTheme.typography.bodySmall)
                Text("Samsung: Battery > Background usage limits: remove COROS from Sleeping and Deep sleeping apps.", style = MaterialTheme.typography.bodySmall)
                Text("OnePlus/OPPO: Battery > Allow background activity and Allow auto-launch for COROS.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cairn itself", style = MaterialTheme.typography.titleSmall)
                Text("Battery: ${BatteryCheck.label(cairnIgnoring)}")
                Text("Cairn should also be unrestricted so its periodic refresh is not delayed.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Battery optimisation list") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Cairn app info") }
            }
        }
    }
}

@Composable
fun SettingsScreen(onOpenFreshness: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val scope = rememberCoroutineScope()
    val on by graph.nudgeSettings.enabled.collectAsState(initial = false)
    val hours by graph.nudgeSettings.thresholdHours.collectAsState(initial = NudgeSettings.DEFAULT)
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        scope.launch { graph.nudgeSettings.setEnabled(ok) }
    }
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Remind me when COROS hasn't synced", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = on, onCheckedChange = { want ->
                if (!want) { scope.launch { graph.nudgeSettings.setEnabled(false); graph.notifier.cancel() } }
                else if (graph.notifier.hasPermission()) { denied = false; scope.launch { graph.nudgeSettings.setEnabled(true) } }
                else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        Text(
            "Cairn shows at most one quiet notification per day, between 08:00 and 22:00, when the newest data is older than the threshold. Tapping it opens COROS so it can sync. Nothing is sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (denied) Text("Notifications are blocked for Cairn, so the reminder stays off. Allow them in system settings to use it.", color = MaterialTheme.colorScheme.error)
        Text("Remind me after", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NudgeSettings.CHOICES.forEach { h ->
                FilterChip(selected = hours == h, enabled = on, onClick = { scope.launch { graph.nudgeSettings.setThreshold(h) } }, label = { Text("$h h") })
            }
        }
        OutlinedButton(onClick = onOpenFreshness) { Text("Keep data fresh") }
    }
}
