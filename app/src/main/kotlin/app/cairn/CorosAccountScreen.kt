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

@Composable
fun CorosAccountScreen(vm: CorosViewModel, modifier: Modifier = Modifier, autoSignIn: Boolean = false, onDone: () -> Unit = {}) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    // Tell the coordinator when we are back in front (drives the gentle "no result yet" hint).
    LifecycleResumeEffect(Unit) { vm.onResumed(); onPauseOrDispose { } }
    // Notifications are optional: the local method's foreground service runs either way.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.signInLocal() }
    fun startLocal() {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.signInLocal()
    }
    val flow = s.signIn
    // Opened from the banner's "Sign in": start the attempt once, only when signed out and nothing is running.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoSignIn) {
        if (autoSignIn && !autoStarted && !s.signedIn && SignInMachine.startEnabled(flow)) { autoStarted = true; vm.signIn(context) }
    }
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.status, style = MaterialTheme.typography.titleMedium)
        var advanced by rememberSaveable { mutableStateOf(false) }
        var trouble by rememberSaveable { mutableStateOf(false) }
        if (s.signedIn && s.needsSignIn) {
            Text("COROS needs you to sign in again before Cairn can refresh.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { vm.signIn(context) }, modifier = Modifier.fillMaxWidth()) { Text("Sign in again") }
        }
        if (!s.signedIn) {
            when (flow) {
                is SignInState.WaitingForBrowser -> {
                    s.deviceCode?.let { Text("Code: $it", style = MaterialTheme.typography.headlineSmall) }
                    Text(
                        if (flow.method == SignInMethod.DEVICE) "Enter the code on the COROS page that opened, then approve. Cairn will notice by itself."
                        else "Finish signing in in the browser, then tap the button on the COROS page if Cairn doesn't open by itself.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.resumeHint) Text(
                        "Still nothing? You can open the sign-in page again, or try the options under \"Having trouble signing in?\".",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    if (flow.method != SignInMethod.DEVICE) OutlinedButton(onClick = vm::reopenSignInPage, modifier = Modifier.fillMaxWidth()) { Text("Open the sign-in page again") }
                }
                is SignInState.Failed -> Text(flow.reason.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            if (SignInMachine.cancelVisible(flow)) OutlinedButton(onClick = vm::cancelSignIn, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Advanced (hide)" else "Advanced") }
            if (advanced) {
                Text("Force a region (no automatic fallback)", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Region.entries.forEach { r ->
                        FilterChip(selected = s.region == r, onClick = { vm.setRegion(r) }, label = { Text(r.label) })
                    }
                }
            }
            val enabled = SignInMachine.startEnabled(flow)
            Button(onClick = { vm.signIn(context, forced = advanced) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (flow is SignInState.Failed) "Try again" else "Sign in")
            }
            val failedRegion = s.failedRegion
            if (flow is SignInState.Failed && failedRegion != null && RegionPolicy.next(failedRegion) != null) {
                OutlinedButton(onClick = { vm.tryAnotherRegion(context) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Try another region") }
            }
            TextButton(onClick = { trouble = !trouble }) { Text(if (trouble) "Having trouble signing in? (hide)" else "Having trouble signing in?") }
            if (trouble || s.resumeHint || flow is SignInState.Failed) {
                Text("These cancel the current attempt and start a new one.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = ::startLocal, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Try the local method") }
                OutlinedButton(onClick = { vm.signInDeviceCode(context) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Sign in with device code") }
            }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            OutlinedButton(onClick = vm::signOut, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            if (BuildConfig.DEBUG) DevTools.Section(s, vm)
        }
        SignInDiagLines(s.diag)
    }
}

/** Secret-free sign-in diagnostics (method, stage, last failure) for bug reports. */
@Composable
fun SignInDiagLines(d: SignInDiag, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Sign-in diagnostics", style = MaterialTheme.typography.titleSmall)
        Text("Method: ${d.method ?: "none yet"}", style = MaterialTheme.typography.bodySmall)
        Text("Stage reached: ${d.stage}", style = MaterialTheme.typography.bodySmall)
        Text("Last failure: ${d.failure ?: "none"}", style = MaterialTheme.typography.bodySmall)
    }
}
