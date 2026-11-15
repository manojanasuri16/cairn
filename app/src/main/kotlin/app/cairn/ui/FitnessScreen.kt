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
fun FitnessScreen(onOpenAccount: () -> Unit, onAddWidget: (app.cairn.widget.WidgetKind) -> Unit, modifier: Modifier = Modifier, vm: FitnessViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    FitnessContent(state, onOpenAccount, modifier, onAddWidget, header = { SyncStatusBar(state.footer) })
}

@Composable
fun FitnessContent(state: FitnessState, onOpenAccount: () -> Unit, modifier: Modifier = Modifier, onAddWidget: (app.cairn.widget.WidgetKind) -> Unit = {}, header: @Composable () -> Unit = {}) {
    var sheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<app.cairn.widget.WidgetKind?>(null) }
    sheet?.let { CardActionsSheet(it, onAddWidget, onDismiss = { sheet = null }) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md).padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        header()
        state.empty?.let { EmptyState(it, onOpenAccount) }
        state.chart?.let { c ->
            val p = LocalCardColors.current
            val ratioValue = state.ratio?.replace(',', '.')?.toDoubleOrNull()
            val color = Color(when { ratioValue == null -> p.cool; ratioValue < 0.8 -> p.cool; ratioValue <= 1.3 -> p.good; ratioValue <= 1.5 -> p.warn; else -> p.bad })
            SectionCard(onLongPress = { sheet = app.cairn.widget.WidgetKind.TrainingLoad }) {
                SectionTitle("Training load, last 7 days")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.ratio?.let { FitText(it, MaterialTheme.typography.displaySmall) }
                    state.comment?.let { LevelPill(it, color) }
                }
                state.ratio?.let { Text("Load ratio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                LoadBars(c.short, c.long, c.description, shortColor = MaterialTheme.colorScheme.primary, longColor = Color(p.cool))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    LegendItem(MaterialTheme.colorScheme.primary, "Short-term")
                    LegendItem(Color(p.cool), "Long-term")
                }
            }
        }
        val level = listOfNotNull(
            state.vo2max?.let { MetricUi("VO2max", it) },
            state.runningLevel?.let { MetricUi("Running level", it) },
            state.thresholdPace?.let { MetricUi("Threshold pace", it) },
        )
        if (level.isNotEmpty()) SectionCard { SectionTitle("Running fitness"); level.forEach { MetricRow(it) } }
        if (state.predictions.isNotEmpty()) SectionCard { SectionTitle("Race predictions"); AdaptiveTiles(state.predictions) }
    }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun FitnessPreview() = CairnTheme {
    FitnessContent(
        FitnessState(
            loading = false, chart = LoadChartUi(listOf(40f, 55f, null, 70f, 62f, 80f, 75f), listOf(50f, 52f, 54f, 55f, 58f, 60f, 62f), "Training load"),
            ratio = "1.21", comment = "Optimal", vo2max = "52", runningLevel = "74", thresholdPace = "4:21 /km",
            predictions = listOf(MetricUi("5K", "21:40"), MetricUi("Marathon", "3:42:10")),
            footer = FooterUi("Updated 3h ago", Freshness.Aging),
        ),
        onOpenAccount = {},
    )
}
