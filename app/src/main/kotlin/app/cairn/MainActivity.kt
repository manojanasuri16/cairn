package app.cairn

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cairn.widget.EXTRA_COROS_MISSING
import app.cairn.widget.EXTRA_SCREEN
import app.cairn.widget.PinnableWidget
import app.cairn.widget.Screen
import app.cairn.widget.WidgetPinner

class MainActivity : ComponentActivity() {
    private val coros: CorosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CairnApp(coros, intent?.getStringExtra(EXTRA_SCREEN), intent?.getBooleanExtra(EXTRA_COROS_MISSING, false) == true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CairnApp(coros: CorosViewModel, startScreen: String?, corosMissing: Boolean) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // minSdk 34, so dynamic colour is always available.
    val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    var inspector by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(startScreen == Screen.Account.name) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var fresh by rememberSaveable { mutableStateOf(startScreen == Screen.Freshness.name) }
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = inspector || account || diagnostics || fresh || settings) { inspector = false; account = false; diagnostics = false; fresh = false; settings = false }
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { CenterAlignedTopAppBar(title = { Text(if (inspector) "Data sources" else if (account) "COROS account" else if (diagnostics) "Sync diagnostics" else if (fresh) "Keep data fresh" else if (settings) "Settings" else "Cairn") }) },
        ) { padding ->
            if (inspector) {
                InspectorScreen(Modifier.padding(padding))
            } else if (account) {
                CorosAccountScreen(coros, Modifier.padding(padding))
            } else if (diagnostics) {
                SyncDiagnosticsScreen(Modifier.padding(padding))
            } else if (fresh) {
                FreshnessScreen(corosMissing, Modifier.padding(padding))
            } else if (settings) {
                SettingsScreen({ settings = false; fresh = true }, Modifier.padding(padding))
            } else {
                Column(Modifier.padding(padding)) {
                    TextButton(onClick = { fresh = true }) { Text("Keep data fresh") }
                    TextButton(onClick = { settings = true }) { Text("Settings") }
                    TextButton(onClick = { inspector = true }) { Text("Data sources") }
                    TextButton(onClick = { account = true }) { Text("COROS account") }
                    TextButton(onClick = { diagnostics = true }) { Text("Sync diagnostics") }
                    if (WidgetPinner.isSupported(context)) {
                        Text("Add widget", Modifier.padding(start = 12.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge)
                        PinnableWidget.entries.forEach { w ->
                            TextButton(onClick = { WidgetPinner.pin(context, w) }) { Text(w.label) }
                        }
                    }
                }
            }
        }
    }
}
