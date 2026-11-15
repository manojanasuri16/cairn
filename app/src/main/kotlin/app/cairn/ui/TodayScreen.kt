package app.cairn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.LocalCairnPalette
import app.cairn.core.designsystem.LocalCardColors
import kotlin.math.roundToInt
import app.cairn.core.designsystem.ProgressRing
import app.cairn.core.designsystem.SegmentBar
import app.cairn.core.designsystem.Sparkline
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(snackbar: SnackbarHostState, onOpenAccount: () -> Unit, modifier: Modifier = Modifier, vm: TodayViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(vm) {
        vm.refreshResults.collect { r ->
            // Haptic on the same frame the result is shown.
            if (r.positive) view.confirmHaptic() else view.rejectHaptic()
            snackbar.showSnackbar(r.message)
        }
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = modifier.fillMaxSize()) {
        TodayContent(state, onOpenAccount)
    }
}

@Composable
fun TodayContent(state: TodayState, onOpenAccount: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        state.footer?.let { AgeLine(it) }
        state.empty?.let { EmptyState(it, onOpenAccount) }
        state.recovery?.let { r ->
            val p = LocalCairnPalette.current
            val color = Color(p.recovery((r.fraction * 100).roundToInt()))
            HeroCard {
                SectionTitle("Recovery", onHero = true)
                // The ring grows with the font scale so the numeral never clips at 200%.
                val ring = (168 * LocalDensity.current.fontScale.coerceIn(1f, 1.75f)).dp
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ProgressRing(r.fraction, r.description, size = ring, color = color, track = Color(p.track)) {
                        FitText(r.valueText, MaterialTheme.typography.displayMedium, Modifier.widthIn(max = ring * 0.62f), color = Color(p.ink), floor = 20.sp, textAlign = TextAlign.Center)
                    }
                    state.recoveryLevel?.let { LevelPill(it, color) }
                    state.timeToFull?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(p.inkSub)) }
                }
            }
        }
        state.steps?.let { s ->
            SectionCard {
                SectionTitle("Steps")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProgressRing(s.fraction, s.description, size = 80.dp, stroke = 8.dp, color = MaterialTheme.colorScheme.primary, track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)) {}
                    Column(Modifier.weight(1f)) {
                        FitText(s.valueText, MaterialTheme.typography.headlineLarge, Modifier.fillMaxWidth(), floor = 20.sp)
                        Text(s.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        state.sleep?.let { SleepCard(it) }
        if (state.metrics.isNotEmpty()) {
            SectionCard {
                SectionTitle("Vitals")
                state.metrics.forEach { MetricRow(it) }
            }
        }
        if (!state.loading) {
            SectionCard {
                SectionTitle("Last 7 days")
                state.sparks.forEach { sp ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sp.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(sp.latest ?: "No data", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                        }
                        Sparkline(sp.values, sp.description)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepCard(s: SleepUi) {
    SectionCard {
        SectionTitle("Sleep")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs), itemVerticalAlignment = Alignment.Bottom) {
            Text(s.durationText, style = MaterialTheme.typography.headlineLarge)
            s.scoreText?.let { Text("Score $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        s.window?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (s.stages.isNotEmpty()) {
            val p = LocalCardColors.current
            fun color(k: StageKind): Color = Color(when (k) {
                StageKind.Deep -> p.deep
                StageKind.Light -> p.light
                StageKind.Rem -> p.rem
                StageKind.Awake -> p.awake
            })
            SegmentBar(s.stages.map { it.minutes.toFloat() to color(it.kind) }, s.description)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                s.stages.forEach { st ->
                    LegendItem(color(st.kind), "${st.label} ${st.text}")
                }
            }
        }
    }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun TodayPreview() = CairnTheme {
    TodayContent(
        TodayState(
            loading = false,
            recovery = RingUi(0.82f, "82%", "Well recovered", "Recovery 82 percent"), recoveryLevel = "Well recovered", timeToFull = "Full in 4h",
            steps = RingUi(0.62f, "6,204", "steps", "Steps 6,204 of 10,000 goal"),
            sleep = SleepUi("7h 41m", "84", "23:10–07:02", listOf(StageUi(StageKind.Deep, "Deep", 90, "1h 30m"), StageUi(StageKind.Light, "Light", 250, "4h 10m"), StageUi(StageKind.Rem, "REM", 100, "1h 40m")), "Sleep 7h 41m"),
            metrics = listOf(MetricUi("Resting heart rate", "52 bpm"), MetricUi("Calories", "1,820 kcal")),
            sparks = listOf(SparkUi("Steps", listOf(3f, 5f, null, 8f, 6f, 9f, 7f), "7,000", "Steps")),
            footer = FooterUi("Updated 2h ago", Freshness.Fresh),
        ),
        onOpenAccount = {},
    )
}
