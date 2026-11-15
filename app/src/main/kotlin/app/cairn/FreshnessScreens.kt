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

/** Sync status line plus a Sync now button. Progress and failures show in the line and the inline banner, never in a toast. */
@Composable
fun SyncNowButton(modifier: Modifier = Modifier, vm: app.cairn.ui.SyncStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ui by vm.ui.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        app.cairn.ui.SyncStatusBar(null, vm = vm)
        Button(enabled = !ui.syncing, onClick = vm::sync, modifier = Modifier.fillMaxWidth()) { Text(if (ui.syncing) "Syncing…" else "Sync now") }
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
                "Opening COROS lets it sync, and Cairn then refreshes right away and again about 90 seconds and 6 minutes later.",
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
