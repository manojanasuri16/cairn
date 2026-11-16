package app.cairn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.cairn.source.corosmcp.SignInDiag
import app.cairn.source.corosmcp.SignInMachine
import app.cairn.source.corosmcp.SignInMethod
import app.cairn.source.corosmcp.SignInState
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.cairn.source.corosmcp.RegionPolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cairn.source.corosmcp.Region

object DevTools {
    @Composable
    fun Section(s: CorosUiState, vm: CorosViewModel) {
        var dev by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { dev = !dev }) { Text(if (dev) "Developer tools (hide)" else "Developer tools") }
        if (dev) Body(s, vm)
    }

    @Composable
    fun Body(s: CorosUiState, vm: CorosViewModel) {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            run {
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
}
