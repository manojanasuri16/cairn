package app.cairn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExplorerScreen(vm: ExplorerViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (s.tools.isEmpty()) vm.load() }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(s.status, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = vm::captureAll, enabled = !s.busy && s.tools.isNotEmpty()) { Text("Capture all read-only tools") }
            Text("Writes redacted replies to app-private files/explorer. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall)
        }
        items(s.tools, key = { it.info.name }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.info.name, fontWeight = FontWeight.Bold)
                    Text(if (t.callable) "Callable" else "Blocked: ${t.blockReason}", style = MaterialTheme.typography.labelMedium)
                    if (t.info.description.isNotBlank()) Text(t.info.description.take(300), style = MaterialTheme.typography.bodySmall)
                    t.schemaLines.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    s.progress[t.info.name]?.let { Text("Capture: $it", style = MaterialTheme.typography.labelMedium) }
                    if (t.callable) Button(onClick = { vm.call(t.info.name) }, enabled = !s.busy) { Text("Call") }
                    s.results[t.info.name]?.let { r ->
                        r.note?.let { Text(it) }
                        if (r.shape.isNotEmpty()) Text(r.shape, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (r.sample.isNotEmpty()) Text(r.sample, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
