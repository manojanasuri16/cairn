package app.cairn

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CairnApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CairnApp() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // minSdk 34, so dynamic colour is always available.
    val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    var inspector by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = inspector) { inspector = false }
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { CenterAlignedTopAppBar(title = { Text(if (inspector) "Data sources" else "Cairn") }) },
        ) { padding ->
            if (inspector) {
                InspectorScreen(Modifier.padding(padding))
            } else {
                Column(Modifier.padding(padding)) {
                    TextButton(onClick = { inspector = true }) { Text("Data sources") }
                }
            }
        }
    }
}
