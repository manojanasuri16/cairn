package app.cairn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cairn.core.common.RelativeAge
import app.cairn.sync.StopReasons
import app.cairn.sync.SyncScheduler
import app.cairn.sync.SyncSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val clockFmt = DateTimeFormatter.ofPattern("MMM d HH:mm")

@Composable
fun SyncDiagnosticsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val state by graph.syncState.state.collectAsState(initial = app.cairn.sync.SyncState())
    val interval by graph.syncSettings.intervalMinutes.collectAsState(initial = SyncSettings.DEFAULT)
    val periodic by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_NAME) }
        .collectAsState(initial = emptyList<WorkInfo>())
    val scope = rememberCoroutineScope()
    val now = Instant.now()
    fun at(ms: Long?) = ms?.let { Instant.ofEpochMilli(it) }?.let { "${clockFmt.format(it.atZone(ZoneId.systemDefault()))} (${RelativeAge.format(it, now).removePrefix("Updated ")})" } ?: "never"

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SyncNowButton()

        Text("Interval", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncSettings.CHOICES.forEach { m ->
                FilterChip(selected = interval == m, onClick = { scope.launch { graph.syncScheduler.setInterval(m) } }, label = { Text("$m min") })
            }
        }
        if (interval == SyncSettings.FAST) Text("Every 5 minutes uses more battery and COROS requests, and Android may delay it. Those runs are light (recovery and daily only).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section("Status") {
            Line("Last attempt", at(state.lastAttemptMs))
            Line("Last success", at(state.lastSuccessMs))
            Line("Last error", state.lastErrorType ?: "none")
            if (state.needsSignIn) Text("COROS needs sign-in. MCP calls are paused until you sign in again.", color = MaterialTheme.colorScheme.error)
            Line("COROS requests today", "${state.mcpCallsToday} of ${app.cairn.sync.McpPlanner.DAILY_CAP}")
        }
        Section("Sign-in") { SignInDiagLines(graph.signIn.diag.collectAsState().value) }
        Section("Data age") {
            Line("Health Connect", at(state.lastHcDataMs))
            Line("COROS MCP (last change)", at(state.lastMcpDataChangeMs))
            Line("Last MCP run", at(state.lastMcpRunMs))
        }
        Section("Scheduler") {
            val info = periodic.firstOrNull()
            Line(if (interval == SyncSettings.FAST) "5 minute chain" else "Periodic job", if (interval == SyncSettings.FAST) "self-rescheduling, best effort" else (info?.state?.name ?: "not scheduled"))
            Line("Platform stop reason", info?.stopReason?.let { StopReasons.name(it) } ?: "n/a")
            if (state.stops.isEmpty()) Line("Recorded stops", "none")
            state.stops.forEach { Line(at(it.atMs), it.name) }
        }
        Section("Recent runs") {
            if (state.runs.isEmpty()) Text("No runs yet", style = MaterialTheme.typography.bodySmall)
            state.runs.forEach { r ->
                Column {
                    Text("${at(r.atMs)}  ${r.reason}  ${r.outcome}", style = MaterialTheme.typography.bodyMedium)
                    val detail = listOfNotNull(
                        if (r.hcRead) "HC read" else null,
                        r.mcpCalls.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        r.errors.takeIf { it.isNotEmpty() }?.let { "errors: ${it.joinToString()}" },
                        r.note,
                    ).joinToString(" | ")
                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
