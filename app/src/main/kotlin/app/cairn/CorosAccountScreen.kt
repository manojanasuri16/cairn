package app.cairn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cairn.source.corosmcp.Region

@Composable
fun CorosAccountScreen(vm: CorosViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.status, style = MaterialTheme.typography.titleMedium)
        s.deviceCode?.let { Text("Code: $it", style = MaterialTheme.typography.headlineSmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Region.entries.forEach { r ->
                FilterChip(selected = s.region == r, enabled = !s.signedIn && !s.busy, onClick = { vm.setRegion(r) }, label = { Text(r.label) })
            }
        }
        if (!s.signedIn) {
            Button(onClick = { vm.signIn(context) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
            OutlinedButton(onClick = { vm.signInDeviceCode(context) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in with device code") }
        } else {
            OutlinedButton(onClick = vm::signOut, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            Button(onClick = vm::listTools, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("List tools") }
            val callable = if (s.tools.isEmpty()) emptyList() else s.tools.filter { it in INTERESTING_TOOLS }.ifEmpty { s.tools }
            if (s.tools.isNotEmpty()) Text("Tools: " + s.tools.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            callable.forEach { t ->
                OutlinedButton(onClick = { vm.call(t) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Call $t") }
            }
            s.shape?.let { (tool, shape) ->
                Text("Shape of $tool", style = MaterialTheme.typography.titleSmall)
                Text(shape, style = MaterialTheme.typography.bodySmall)
            }
            if (s.canCopy) {
                Button(onClick = {
                    vm.redactedSample()?.let {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("redacted sample", it))
                        Toast.makeText(context, "Copied. Review before sharing.", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy redacted sample") }
            }
        }
    }
}
