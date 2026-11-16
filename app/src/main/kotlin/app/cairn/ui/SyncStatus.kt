package app.cairn.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.CairnApp
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.LocalCardColors
import app.cairn.core.designsystem.LocalReducedMotion
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.Freshness
import app.cairn.sync.Indicator
import app.cairn.sync.IndicatorKind
import app.cairn.sync.ManualSync
import app.cairn.sync.StatusLine
import app.cairn.sync.SyncIndicators
import app.cairn.widget.FooterUi
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the header status line and error banner draw. */
data class SyncUi(val indicator: Indicator? = null, val upToDate: Boolean = false, val error: String? = null, val syncing: Boolean = false, val errorKind: ProblemKind = ProblemKind.Other)

/** One sync controller shared by every screen: the state comes from the persisted sync state, so it survives navigation. */
class SyncStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    private val footer = MutableStateFlow<FooterUi?>(null)
    // Shared by every screen's controller, so one dismiss or a successful sign-in clears the banner everywhere.
    private val dismissedAt = dismissedAtShared
    private val gateError = gateErrorShared
    private val ticks = flow { while (true) { emit(Unit); delay(1_000) } }

    val ui: StateFlow<SyncUi> = combine(graph.syncState.state, footer, dismissedAt, gateError, ticks) { st, f, dismissed, gate, _ ->
        val now = Instant.now()
        val age = f?.let { Duration.ofMillis(it.ageMs) } ?: st.lastSuccessMs?.let { Duration.between(Instant.ofEpochMilli(it), now) } ?: Duration.ofDays(365)
        val tier = f?.tier ?: Freshness.fromAge(age)
        val ind = SyncIndicators.derive(tier, age, st, now)
        val runMessage = if (gate == null) StatusLine.errorMessage(st, now, dismissed) else null
        val kind = if (gate != null) gate.second else st.runs.firstOrNull().let { r -> SyncProblems.fromOutcome(r?.outcome, r?.note) }
        SyncUi(ind, SyncIndicators.justSucceeded(st, now) && ind.kind != IndicatorKind.Syncing, gate?.first ?: runMessage, ind.kind == IndicatorKind.Syncing, kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    fun setFooter(f: FooterUi?) { footer.value = f }

    private fun online(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Syncs now: no 5 minute wait. Only a 10 s debounce, the offline/sign-in checks and the daily cap can stop it, and each says so. */
    fun sync() {
        viewModelScope.launch {
            val st = graph.syncState.get()
            val signedIn = withContext(Dispatchers.IO) { graph.tokenStore.load() != null || app.cairn.DemoFeature.active }
            when (val g = ManualSync.gate(st, Instant.now(), signedIn, online())) {
                is ManualSync.Gate.Skipped -> if (g.message != "Already syncing.") gateError.value = g.message to SyncProblems.fromGate(signedIn, online(), g.message)
                ManualSync.Gate.Allowed -> { gateError.value = null; graph.syncScheduler.requestUserSync() }
            }
        }
    }

    fun dismissError() {
        gateError.value = null
        dismissedAt.value = System.currentTimeMillis()
    }

    companion object {
        private val dismissedAtShared = MutableStateFlow(0L)
        private val gateErrorShared = MutableStateFlow<Pair<String, ProblemKind>?>(null)

        /** Clears every stale problem (used after a successful sign-in, when the old "not signed in" message no longer applies). */
        fun clearAll() { gateErrorShared.value = null; dismissedAtShared.value = System.currentTimeMillis() }
    }
}

@Composable
private fun dotColor(kind: IndicatorKind, upToDate: Boolean): Color {
    val p = LocalCardColors.current
    return when {
        kind == IndicatorKind.Syncing -> MaterialTheme.colorScheme.onSurfaceVariant
        upToDate -> Color(p.good)
        kind == IndicatorKind.Fresh -> Color(p.good)
        kind == IndicatorKind.Aging -> Color(p.warn)
        else -> Color(p.bad)
    }
}

/**
 * Quiet status line for a screen header: a coloured dot (green fresh, amber getting old, red stale), a very short age only when
 * data is old, and the sync button. Syncing spins the icon (a still clock with reduced motion); success shows a tonal
 * "Up to date" for a few seconds. The exact age is the spoken description and appears on long press.
 */
@Composable
fun SyncStatusBar(footer: FooterUi?, modifier: Modifier = Modifier, vm: SyncStatusViewModel = viewModel()) {
    LaunchedEffect(footer) { vm.setFooter(footer) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val view = LocalView.current
    val reduced = LocalReducedMotion.current
    val ind = ui.indicator
    var showAge by remember { mutableStateOf(false) }
    LaunchedEffect(showAge) { if (showAge) { delay(3_000); showAge = false } }
    LaunchedEffect(ui.upToDate) { if (ui.upToDate) view.confirmHaptic() }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ind != null) {
                val text = StatusLine.text(ind, ui.upToDate)
                val onSurf = MaterialTheme.colorScheme.background.toArgb()
                val textColor = when {
                    ui.upToDate -> MaterialTheme.colorScheme.onSecondaryContainer
                    ind.kind == IndicatorKind.Stale -> Color(Contrast.ensure(LocalCardColors.current.bad, listOf(onSurf), 4.5))
                    ind.kind == IndicatorKind.Aging -> Color(Contrast.ensure(LocalCardColors.current.warn, listOf(onSurf), 4.5))
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.semantics { contentDescription = ind.description; onLongClick("Show exact age") { showAge = true; true } }
                        .let { m -> if (ui.upToDate) m.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)).padding(horizontal = Spacing.sm, vertical = Spacing.xxs) else m },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (ui.upToDate) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = textColor)
                    else Box(Modifier.size(10.dp).background(dotColor(ind.kind, false), CircleShape))
                    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { t ->
                        if (t != null) Text(t, style = MaterialTheme.typography.labelLarge, color = textColor)
                    }
                }
            }
            Box(Modifier.weight(1f))
            val angle by if (ui.syncing && !reduced) rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
            else remember { mutableStateOf(0f) }
            IconButton(onClick = vm::sync, enabled = !ui.syncing, modifier = Modifier.semantics { contentDescription = if (ui.syncing) "Syncing" else "Sync now" }) {
                Icon(if (ui.syncing && reduced) AppIcons.Clock else AppIcons.Sync, null, Modifier.rotate(angle), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showAge && ind != null) Text(ind.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.xs))
        val openAccount = LocalOpenAccount.current
        SyncErrorBanner(ui.error, ui.errorKind, onRetry = vm::sync, onSignIn = { openAccount(SyncProblems.startsSignIn(ui.errorKind)) }, onDismiss = vm::dismissError)
    }
}

/** Failure shown inline as a dismissible tonal card with a retry action, never as a bottom toast. */
@Composable
fun SyncErrorBanner(message: String?, kind: ProblemKind, onRetry: () -> Unit, onSignIn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val action = SyncProblems.actionFor(kind)
    val label = SyncProblems.label(action, kind)
    Card(
        modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
    ) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(AppIcons.Warning, null, Modifier.size(20.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (label != null) TextButton(onClick = if (action == BannerAction.SignIn) onSignIn else onRetry) { Text(label) }
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Dismiss") }
        }
    }
}

/** Opens the COROS account screen; the flag asks it to start sign-in right away (only when signed out is certain). Provided by the scaffold. */
val LocalOpenAccount = androidx.compose.runtime.staticCompositionLocalOf<(Boolean) -> Unit> { {} }
