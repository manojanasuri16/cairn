package app.cairn.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.CorosViewModel
import app.cairn.core.designsystem.CairnMotion
import app.cairn.core.designsystem.LocalReducedMotion
import app.cairn.core.designsystem.Spacing
import app.cairn.isCorosInstalled
import app.cairn.source.corosmcp.SignInMessages
import app.cairn.source.healthconnect.HealthConnectReader
import app.cairn.source.healthconnect.syncReadPermissions
import app.cairn.sync.BatteryCheck
import app.cairn.widget.COROS_PACKAGE
import app.cairn.widget.PinnableWidget
import app.cairn.widget.WidgetPinner

private const val STEPS = 3

/** Three short, skippable steps shown once: sources, connect, background check plus widget. */
@Composable
fun OnboardingScreen(coros: CorosViewModel, modifier: Modifier = Modifier, vm: OnboardingViewModel = viewModel(), settings: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var choice by rememberSaveable { mutableStateOf(OnboardingChoice.Both) }
    val hc by settings.state.collectAsStateWithLifecycle()
    val corosState by coros.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.refreshSources() }
    val grant = rememberLauncherForActivityResult(HealthConnectReader.permissionContract()) { settings.onPermissionResult() }
    BackHandler(enabled = step > 0) { step -= 1 }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Step ${step + 1} of $STEPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = vm::finish) { Text("Skip setup") }
            }
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (reduced) fadeIn() togetherWith fadeOut()
                    else (slideInHorizontally(CairnMotion.settle()) { if (targetState > initialState) it / 4 else -it / 4 } + fadeIn(CairnMotion.settle())) togetherWith
                        (slideOutHorizontally(CairnMotion.settle()) { if (targetState > initialState) -it / 4 else it / 4 } + fadeOut(CairnMotion.settle()))
                },
                label = "onboarding",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    when (s) {
                        0 -> {
                            Text("Where does your data come from?", style = MaterialTheme.typography.headlineMedium)
                            Text("Cairn only reads. You can change this later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                OnboardingChoice.entries.forEach { c ->
                                    Card(Modifier.fillMaxWidth().selectable(choice == c, role = Role.RadioButton, onClick = { choice = c })) {
                                        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = choice == c, onClick = null, modifier = Modifier.padding(Spacing.xs))
                                            Text(c.label, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text("Connect your sources", style = MaterialTheme.typography.headlineMedium)
                            if (choice.useHealthConnect) SectionCard {
                                Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                                Text(SourceStatus.label(hc.hc), style = MaterialTheme.typography.bodyMedium)
                                Text("Cairn asks only for steps, heart rate, sleep and exercise.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (SourceStatus.canGrant(hc.hc)) Button(onClick = { grant.launch(syncReadPermissions) }) { Text("Grant Health Connect access") }
                            }
                            if (choice.useCoros) SectionCard {
                                Text("COROS account", style = MaterialTheme.typography.titleMedium)
                                Text(if (corosState.signedIn) "Signed in" else corosState.status, style = MaterialTheme.typography.bodyMedium)
                                Text(SignInMessages.HINT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                if (!corosState.signedIn) Button(onClick = { coros.signIn(context) }, enabled = !corosState.busy) { Text("Sign in to COROS") }
                            }
                        }
                        else -> {
                            Text("Keep it fresh, then add a widget", style = MaterialTheme.typography.headlineMedium)
                            if (choice.useCoros) CorosBackgroundSummary()
                            if (WidgetPinner.isSupported(context)) SectionCard {
                                Text("Home screen widget", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { WidgetPinner.pin(context, PinnableWidget.entries.first()) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Add ${PinnableWidget.entries.first().label} widget")
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End)) {
                if (step > 0) TextButton(onClick = { step -= 1 }) { Text("Back") }
                Button(onClick = { if (step < STEPS - 1) step += 1 else vm.finish() }) { Text(if (step < STEPS - 1) "Continue" else "Finish setup") }
            }
        }
    }
}

@Composable
private fun CorosBackgroundSummary() {
    val context = LocalContext.current
    val installed = remember { isCorosInstalled(context) }
    val ignoring = remember {
        if (!installed) null else runCatching { context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(COROS_PACKAGE) }.getOrNull()
    }
    SectionCard {
        Text("COROS background check", style = MaterialTheme.typography.titleMedium)
        Text("Cairn is only as fresh as COROS's last sync, and COROS must be allowed to run in the background.", style = MaterialTheme.typography.bodyMedium)
        Text(if (installed) "COROS is installed. Battery: ${BatteryCheck.label(ignoring)}" else "The COROS app is not installed on this phone.", style = MaterialTheme.typography.bodyMedium)
        if (installed) OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$COROS_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Open COROS app info") }
    }
}
