package app.cairn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cairn.core.common.RelativeAge
import app.cairn.source.healthconnect.BACKGROUND_READ_PERMISSION
import app.cairn.source.healthconnect.COROS_PACKAGE
import app.cairn.source.healthconnect.CorosStatus
import app.cairn.source.healthconnect.HcAvailability
import app.cairn.source.healthconnect.HcFeatures
import app.cairn.source.healthconnect.HealthConnectReader
import app.cairn.source.healthconnect.HealthInspector
import app.cairn.source.healthconnect.SourceKind
import app.cairn.source.healthconnect.TypeRow
import app.cairn.source.healthconnect.inspectedReadPermissions
import app.cairn.source.healthconnect.sourceKind
import java.time.Instant
import kotlinx.coroutines.launch

private data class InspectorState(
    val availability: HcAvailability = HcAvailability.Unavailable,
    val features: HcFeatures? = null,
    val coros: CorosStatus? = null,
    val granted: Set<String> = emptySet(),
    val rows: List<TypeRow> = emptyList(),
    val loading: Boolean = false,
)

@Composable
fun InspectorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reader = remember { HealthConnectReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(InspectorState()) }

    suspend fun refresh() {
        val availability = reader.availability()
        if (availability != HcAvailability.Available) {
            state = InspectorState(availability = availability, coros = reader.corosStatus())
            return
        }
        state = state.copy(availability = availability, loading = true)
        val granted = reader.grantedPermissions()
        val rows = HealthInspector(reader).inspect(Instant.now())
        state = InspectorState(availability, reader.features(), reader.corosStatus(), granted, rows, false)
    }

    val grant = rememberLauncherForActivityResult(HealthConnectReader.permissionContract()) {
        scope.launch { refresh() }
    }
    LaunchedEffect(Unit) { refresh() }

    val allGranted = state.granted.containsAll(inspectedReadPermissions)
    val now = remember(state.rows) { Instant.now() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Health Connect: ${state.availability.label()}", style = MaterialTheme.typography.titleMedium)
                state.features?.let {
                    Text("Background read: ${it.backgroundRead.yn()}  ·  History: ${it.history.yn()}")
                }
                state.coros?.let { Text(it.describe()) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.availability == HcAvailability.Available) {
                    Button(onClick = { grant.launch(inspectedReadPermissions) }, enabled = !allGranted) {
                        Text(if (allGranted) "Access granted" else "Grant access")
                    }
                    if (state.features?.backgroundRead == true &&
                        BACKGROUND_READ_PERMISSION !in state.granted
                    ) {
                        OutlinedButton(onClick = { grant.launch(setOf(BACKGROUND_READ_PERMISSION)) }) {
                            Text("Allow background read")
                        }
                    }
                }
            }
        }
        if (state.loading) item { Text("Reading last 30 days…") }
        items(state.rows, key = { it.type.key }) { row -> TypeRowCard(row, now) }
    }
}

@Composable
private fun TypeRowCard(row: TypeRow, now: Instant) {
    val kind = row.summary?.sourceKind(COROS_PACKAGE) ?: SourceKind.None
    val container = when (kind) {
        SourceKind.Coros -> MaterialTheme.colorScheme.primaryContainer
        SourceKind.Other -> MaterialTheme.colorScheme.surfaceContainerHigh
        SourceKind.None -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.type.label, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        row.error -> "No access"
                        else -> "${row.summary?.count ?: 0} in 30d"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            row.summary?.let { s ->
                s.latestModified?.let { Text(RelativeAge.format(it, now), style = MaterialTheme.typography.bodySmall) }
                Text(
                    when (kind) {
                        SourceKind.Coros -> "COROS: " + s.origins.joinToString()
                        SourceKind.Other -> "Other: " + s.origins.joinToString()
                        SourceKind.None -> "No data"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Boolean.yn() = if (this) "available" else "unavailable"

private fun HcAvailability.label() = when (this) {
    HcAvailability.Available -> "available"
    HcAvailability.UpdateRequired -> "update required"
    HcAvailability.Unavailable -> "unavailable"
}

private fun CorosStatus.describe(): String = when {
    !installed -> "COROS app: not installed"
    batteryUnrestricted == true -> "COROS app: installed, exempt from battery optimisation"
    else -> "COROS app: installed, battery optimised (may sync late)"
}
