package app.cairn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cairn.core.designsystem.ProgressRing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.LocalCardColors
import androidx.compose.ui.graphics.Color
import app.cairn.core.designsystem.LoadBars
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterUi

@Composable
fun FitnessScreen(onOpenAccount: () -> Unit, modifier: Modifier = Modifier, vm: FitnessViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    FitnessContent(state, onOpenAccount, modifier, header = { SyncStatusBar(state.footer) })
}

@Composable
fun FitnessContent(state: FitnessState, onOpenAccount: () -> Unit, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md).padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        header()
        state.empty?.let { EmptyState(it, onOpenAccount) }
        state.status?.let { st ->
            val c = state.chart
            val p = LocalCardColors.current
            val ratioValue = state.ratioValue
            val color = Color(when { ratioValue == null -> p.cool; ratioValue < 0.8 -> p.cool; ratioValue <= 1.3 -> p.good; ratioValue <= 1.5 -> p.warn; else -> p.bad })
            SectionCard(card = CardId.FitTrainingStatus) {
                SectionTitle("Training Status")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    st.status?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
                    state.ratio?.let { LevelPill("Intensity Trend $it", color) }
                }
                listOfNotNull(
                    st.sevenDayLoad?.let { MetricUi("7-Day Training Load", it) }, st.loadImpact?.let { MetricUi("Load Impact", it) },
                    st.baseFitness?.let { MetricUi("Base Fitness", it) },
                ).forEach { MetricRow(it) }
                if (c != null) {
                    LoadBars(c.short, c.long, c.description, shortColor = MaterialTheme.colorScheme.primary, longColor = Color(p.cool))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        LegendItem(MaterialTheme.colorScheme.primary, "Load Impact")
                        LegendItem(Color(p.cool), "Base Fitness")
                    }
                }
            }
        }
        state.running?.let { r ->
            SectionCard(card = CardId.FitRunning) {
                SectionTitle("Running Fitness")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProgressRing(r.fraction, r.description, size = 80.dp, stroke = 8.dp, color = MaterialTheme.colorScheme.primary, track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)) {}
                    Column(Modifier.weight(1f)) {
                        FitText(r.value, MaterialTheme.typography.headlineLarge, Modifier.fillMaxWidth(), floor = 20.sp)
                        r.marathon?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                listOfNotNull(state.vo2max?.let { MetricUi("VO2max", it) }, state.thresholdPace?.let { MetricUi("Threshold pace", it) }).forEach { MetricRow(it) }
            }
        }
        // Without a running level the other figures still show.
        if (state.running == null) {
            val level = listOfNotNull(state.vo2max?.let { MetricUi("VO2max", it) }, state.thresholdPace?.let { MetricUi("Threshold pace", it) })
            if (level.isNotEmpty()) SectionCard(card = CardId.FitVo2) { SectionTitle("Running Fitness"); level.forEach { MetricRow(it) } }
        }
        if (state.predictions.isNotEmpty()) SectionCard(card = CardId.FitRacePredictions) { SectionTitle("Race Predictions"); AdaptiveTiles(state.predictions) }
    }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun FitnessPreview() = CairnTheme {
    FitnessContent(
        FitnessState(
            loading = false, chart = LoadChartUi(listOf(40f, 55f, null, 70f, 62f, 80f, 75f), listOf(50f, 52f, 54f, 55f, 58f, 60f, 62f), "Training load"),
            ratio = "121%", ratioValue = 1.21, comment = "Optimal", vo2max = "52", runningLevel = "73.3", thresholdPace = "4'21\" /km",
            status = TrainingStatusUi("Optimal", "112", "20", "42", "121%", "Training status"),
            running = RunningFitnessUi("73.3", 0.733f, "Marathon 3:42:10", "Running fitness"),
            predictions = listOf(MetricUi("5K", "21:40"), MetricUi("Marathon", "3:42:10")),
            footer = FooterUi("Updated 3h ago", Freshness.Aging),
        ),
        onOpenAccount = {},
    )
}
