package app.cairn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import app.cairn.widget.WidgetKind
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
fun TodayScreen(onOpenAccount: () -> Unit, onAddWidget: (WidgetKind) -> Unit, modifier: Modifier = Modifier, vm: TodayViewModel = viewModel(), sync: SyncStatusViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val syncUi by sync.ui.collectAsStateWithLifecycle()
    // Pull to refresh syncs right away (no 5 minute wait); progress and failures show in the header, not in a bottom toast.
    PullToRefreshBox(isRefreshing = syncUi.syncing, onRefresh = sync::sync, modifier = modifier.fillMaxSize()) {
        TodayContent(state, onOpenAccount, onAddWidget, header = { SyncStatusBar(state.footer) })
    }
}

@Composable
fun TodayContent(state: TodayState, onOpenAccount: () -> Unit, onAddWidget: (WidgetKind) -> Unit = {}, header: @Composable () -> Unit = {}) {
    var sheet by remember { mutableStateOf<WidgetKind?>(null) }
    sheet?.let { CardActionsSheet(it, onAddWidget, onDismiss = { sheet = null }) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        header()
        state.empty?.let { EmptyState(it, onOpenAccount) }
        // The COROS Today order; cards without data are simply absent (no dead space).
        val order = TodayOrder.present(state)
        order.forEach { card ->
            when (card) {
                TodayCard.Header -> HeaderRow(state.header) { sheet = WidgetKind.Today }
                TodayCard.Activity -> state.todayActivity?.let { TodayActivityCard(it) { sheet = WidgetKind.LastActivity } }
                // Heart Rate, Resting HR and Stress are consecutive in COROS: one row of compact tiles.
                TodayCard.HeartRate -> VitalTiles(state)
                TodayCard.RestingHr -> if (TodayCard.HeartRate !in order) VitalTiles(state, first = card)
                TodayCard.Stress -> if (TodayCard.HeartRate !in order && TodayCard.RestingHr !in order) VitalTiles(state, first = card)
                TodayCard.Sleep -> state.sleep?.let { SleepCard(it) { sheet = WidgetKind.Sleep } }
                TodayCard.Hrv -> state.hrv?.let { HrvCard(it) }
                TodayCard.WeeklyLoad -> state.weekly?.let { WeeklyLoadCard(it) { sheet = WidgetKind.TrainingLoad } }
                TodayCard.TrainingStatus -> state.status?.let { TrainingStatusCard(it) { sheet = WidgetKind.TrainingLoad } }
                TodayCard.Recovery -> state.recovery?.let { r -> RecoveryHero(r, state.recoveryLevel, state.timeToFull) { sheet = WidgetKind.Recovery } }
                TodayCard.RunningFitness -> state.running?.let { RunningFitnessCard(it) { sheet = WidgetKind.Fitness } }
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

/** Steps, kcal and active minutes: equal thirds, each number centred over its unit. */
@Composable
private fun HeaderRow(stats: List<StatUi>, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = stats.joinToString(", ") { "${it.value} ${it.label}" } }) {
            stats.forEach { st ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                    Text(st.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Equal-width compact tiles: title, value with its unit on one baseline, a small caption. Height is content plus padding. */
@Composable
private fun VitalTiles(state: TodayState, first: TodayCard = TodayCard.HeartRate) {
    data class Tile(val title: String, val value: String, val unit: String, val caption: String?, val description: String)
    val tiles = listOfNotNull(
        state.heart?.takeIf { first == TodayCard.HeartRate }?.let { Tile("Heart Rate", it.value, it.unit, it.caption + (it.range?.let { r -> " \u00b7 $r" } ?: ""), it.description) },
        state.resting?.let { Tile("Resting HR", it.value, it.unit, listOfNotNull(it.time, it.average).joinToString(" \u00b7 ").ifEmpty { null }, it.description) },
        state.stress?.let { Tile("Stress", it.value, "", it.caption, it.description) },
    )
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tiles.forEach { t ->
            androidx.compose.material3.Card(Modifier.weight(1f).fillMaxHeight(), colors = cairnCardColors()) {
                Column(Modifier.padding(Spacing.sm).semantics(mergeDescendants = true) { contentDescription = t.description }, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(t.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(t.value, style = MaterialTheme.typography.headlineMedium)
                        if (t.unit.isNotEmpty()) Text(t.unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    t.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun TodayActivityCard(a: TodayActivityUi, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = a.description }) {
        SectionTitle("Today's Activity")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(a.sportGlyph, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics { })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                a.distance?.let { FitText(it, MaterialTheme.typography.headlineLarge, Modifier.fillMaxWidth(), floor = 20.sp) }
                val stats = listOfNotNull(a.duration, a.pace)
                if (stats.isNotEmpty()) Text(stats.joinToString("   "), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(a.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(a.title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HrvCard(h: HrvUi) {
    SectionCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = h.description }) {
        SectionTitle("Overnight HRV")
        h.assessment?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
        Text(h.average, style = MaterialTheme.typography.titleMedium)
        listOfNotNull(h.range, h.baseline).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Big 7-day load and the Monday to Sunday mini bars. */
@Composable
private fun WeeklyLoadCard(w: WeeklyLoadUi, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = w.description }) {
        SectionTitle("Weekly Training Load")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(w.total, style = MaterialTheme.typography.headlineLarge)
            w.suggested?.let { Text("Suggested $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
            w.bars.forEach { b ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth(0.55f).height((6 + 42 * b.fraction).dp).background(if (b.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(4.dp)))
                    }
                    Text(b.letter, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        w.note?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TrainingStatusCard(t: TrainingStatusUi, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = t.description }) {
        SectionTitle("Training Status")
        t.status?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
        listOfNotNull(
            t.sevenDayLoad?.let { MetricUi("7-Day Training Load", it) }, t.loadImpact?.let { MetricUi("Load Impact", it) },
            t.baseFitness?.let { MetricUi("Base Fitness", it) }, t.intensityTrend?.let { MetricUi("Intensity Trend", it) },
        ).forEach { MetricRow(it) }
    }
}

@Composable
private fun RunningFitnessCard(r: RunningFitnessUi, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = r.description }) {
        SectionTitle("Running Fitness")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ProgressRing(r.fraction, "", size = 80.dp, stroke = 8.dp, color = MaterialTheme.colorScheme.primary, track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)) {}
            Column(Modifier.weight(1f)) {
                FitText(r.value, MaterialTheme.typography.headlineLarge, Modifier.fillMaxWidth(), floor = 20.sp)
                r.marathon?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Recovery stays the one hero card of the screen; it sits where COROS puts it. */
@Composable
private fun RecoveryHero(r: RingUi, level: String?, timeToFull: String?, onLongPress: () -> Unit) {
    val p = LocalCairnPalette.current
    val color = Color(p.recovery((r.fraction * 100).roundToInt()))
    HeroCard(onLongPress = onLongPress) {
        SectionTitle("Recovery", onHero = true)
        // The ring fills the card: about 70 percent of its width, never above 240 dp, so no dead bands left and right.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ring = HeroFit.ringSize(maxWidth.value).dp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ProgressRing(r.fraction, r.description, size = ring, color = color, track = Color(p.track)) {
                    FitText(r.valueText, MaterialTheme.typography.displayMedium, Modifier.widthIn(max = ring * 0.62f), color = Color(p.ink), floor = 20.sp, textAlign = TextAlign.Center)
                }
                level?.let { LevelPill(it, color) }
                timeToFull?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(p.inkSub)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepCard(s: SleepUi, onLongPress: () -> Unit) {
    SectionCard(onLongPress = onLongPress, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = s.description }) {
        SectionTitle("Sleep")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs), itemVerticalAlignment = Alignment.Bottom) {
            s.scoreText?.let { Text(it, style = MaterialTheme.typography.headlineLarge) }
            s.rating?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
        }
        Text(s.durationText, style = MaterialTheme.typography.titleLarge)
        if (s.stages.isNotEmpty()) {
            val p = LocalCardColors.current
            fun color(k: StageKind): Color = Color(when (k) {
                StageKind.Deep -> p.deep
                StageKind.Light -> p.light
                StageKind.Rem -> p.rem
                StageKind.Awake -> p.awake
            })
            SegmentBar(s.stages.map { it.minutes.toFloat() to color(it.kind) }, s.description)
            s.window?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                s.stages.forEach { st -> LegendItem(color(st.kind), "${st.label} ${st.text}") }
            }
        } else s.window?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun TodayPreview() = CairnTheme {
    TodayContent(
        TodayState(
            loading = false,
            header = listOf(StatUi("9,443", "steps"), StatUi("796", "kcal"), StatUi("55", "min")),
            todayActivity = TodayActivityUi("1", "\uD83C\uDFC3", "Sunday  Park Run", "Sunday, 15 Nov", "5.55 km", "54:37", "9'50\" /km", "Today's activity"),
            heart = HeartUi("70", "bpm", "Daily average", "44-154", "Heart rate"),
            resting = RestingUi("51", "bpm", "Updated 12:05", "7-day avg 52 bpm", "Resting"),
            stress = StressUi("30", "Average Stress", "Stress"),
            sleep = SleepUi("6 h 47 min", "87", "Optimal", "23:10\u201307:02", listOf(StageUi(StageKind.Deep, "Deep", 90, "1 h 30 min"), StageUi(StageKind.Light, "Light", 250, "4 h 10 min"), StageUi(StageKind.Rem, "REM", 100, "1 h 40 min")), "Sleep"),
            hrv = HrvUi("Above normal", "Average 65 ms", "Normal Range 46-58 ms", "Baseline 52 ms", "HRV"),
            weekly = WeeklyLoadUi("112", null, "MTWTFSS".mapIndexed { i, c -> WeekBarUi(c.toString(), if (i == 6) 1f else 0.3f, i == 6) }, null, "Weekly"),
            status = TrainingStatusUi("Decreasing", "112", "20", "42", "47%", "Status"),
            recovery = RingUi(0.99f, "99 %", "Fresh", "Recovery 99 percent"), recoveryLevel = "Fresh", timeToFull = "3 hrs till full recovery",
            running = RunningFitnessUi("73.3", 0.733f, "Marathon 4:09:57", "Running fitness"),
            sparks = listOf(SparkUi("Steps", listOf(3f, 5f, null, 8f, 6f, 9f, 7f), "7,000", "Steps")),
            footer = FooterUi("Updated 2h ago", Freshness.Fresh),
        ),
        onOpenAccount = {},
    )
}

/** Sizing rule for the Recovery hero so the ring wraps the card with even padding. */
object HeroFit {
    const val MAX_DP = 240f
    const val MIN_DP = 120f
    fun ringSize(contentWidthDp: Float): Float = (contentWidthDp * 0.7f).coerceIn(MIN_DP, MAX_DP).coerceAtMost(contentWidthDp.coerceAtLeast(MIN_DP))
}
