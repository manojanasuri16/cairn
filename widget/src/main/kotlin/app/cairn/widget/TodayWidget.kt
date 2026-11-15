package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

class TodayWidget : CairnWidget<TodayUi>(WidgetKind.Today) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<TodayUi>> = WidgetEnvironment.today(unit)
    override fun sample() = Samples.today()

    @Composable
    override fun Body(ui: WidgetUi.Content<TodayUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val plan = currentPlan(chars = (b.stepsText ?: "\u2013").length, heroText = b.stepsText ?: "\u2013")
        val shown = b.metrics.filter { cfg.shows(Part.ofMetric(it.id)) }
        WidgetLog.d("today LocalSize=${widgetSize().first}x${widgetSize().second} dp style=${cfg.style} plan[rows=${plan.count("r")} blocks=${plan.blocks} ring=${plan.heroDp} side=${plan.side}] metricsAvailable=${shown.size}")
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.TodayStack -> StackStyle(b, shown, plan)
                WidgetStyle.TodayStripe -> StripeStyle(b, shown, plan)
                else -> RingStyle(b, shown, plan)
            }
        }
    }
}

class TodayWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = TodayWidget()
}

@Composable
private fun StepsRing(b: TodayUi, plan: Layouts.Plan) {
    val p = skin.p
    val ring = plan.heroDp
    Ring(b.stepsFraction, ring.dp, Layouts.ringStroke(ring).dp, p.accent) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(b.stepsText ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
            if (ring >= 72) Sub(if (b.stepsText == null) "no steps" else "steps", 10f)
        }
    }
}

@Composable
private fun Rows(shown: List<Metric>, count: Int, compact: Boolean, emptyHint: Boolean, availW: Float) {
    val p = skin.p
    val scale = LocalFontScale
    if (shown.isEmpty() && emptyHint) Sub("No other data yet", 12f)
    shown.take(count).forEach { m ->
        // The value is never cut: the label shortens ("RHR") and is dropped only when even that would not fit beside the value.
        // Labels are low priority and never dropped: full, else shortened. The weighted label may clip before the value ever does.
        val label = Layouts.rowLabel(m.label, m.value, availW, scale) ?: Layouts.shortLabel(m.label)
        MetricLine(label, m.value, metricIcon(m.id), metricTint(m.id, p), false, GlanceModifier.height(20.dp))
    }
}

private fun metricTint(id: MetricId, p: app.cairn.core.designsystem.Palette): Int = when (id) {
    MetricId.Sleep -> p.stageDeep
    MetricId.RestingHr -> p.stageRem
    MetricId.Recovery -> p.good
    MetricId.Calories -> p.warn
    MetricId.Stress -> p.stageAwake
}

@Composable
private fun RingStyle(b: TodayUi, shown: List<Metric>, plan: Layouts.Plan) {
    val (w, _) = widgetSize()
    val rows = plan.count("r")
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StepsRing(b, plan)
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { Rows(shown, rows, false, true, w - 2 * plan.pad - plan.heroDp - 16f) }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            StepsRing(b, plan)
            if (rows > 0 && shown.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Rows(shown, rows, compact = w < 140f, emptyHint = false, availW = w - 2f * plan.pad)
            }
        }
    }
}

@Composable
private fun StackStyle(b: TodayUi, shown: List<Metric>, plan: Layouts.Plan) {
    val p = skin.p
    val (w, _) = widgetSize()
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Txt(b.stepsText ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
        if (plan.has("label")) Sub(if (b.stepsText == null) "no steps yet" else "steps today", 12f)
        if (plan.has("bar")) {
            Spacer(GlanceModifier.height(6.dp))
            HBar((w - 2 * plan.pad).dp, 6.dp, b.stepsFraction, p.accent)
            Spacer(GlanceModifier.height(6.dp))
        }
        Rows(shown, plan.count("r"), compact = w < 130f, emptyHint = false, availW = w - 2f * plan.pad)
    }
}

@Composable
private fun StripeStyle(b: TodayUi, shown: List<Metric>, plan: Layouts.Plan) {
    val p = skin.p
    data class Chip(val icon: Int, val tint: Int, val text: String)
    val chips = (listOf(Chip(R.drawable.ic_w_steps, p.accent, b.stepsText ?: "–")) + shown.map { Chip(metricIcon(it.id), metricTint(it.id, p), it.value) }).take(plan.count("c"))
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        chips.chunked(plan.perRow).forEach { line ->
            Row(GlanceModifier.fillMaxWidth().height(Layouts.stripeRowDp(LocalFontScale).dp), verticalAlignment = Alignment.CenterVertically) {
                line.forEach { c ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(c.icon, 18.dp, c.tint)
                        Txt(c.text, 13f, FontWeight.Bold, maxLines = 1)
                    }
                }
                repeat(plan.perRow - line.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}
