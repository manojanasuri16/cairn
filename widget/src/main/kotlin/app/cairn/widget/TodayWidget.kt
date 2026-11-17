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
import androidx.glance.text.TextAlign
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
        // Full label, else the short one, else icon only: never an ellipsized stub such as "S…".
        // A long value ("7 h 41 min · 82") steps down in size before it would be cut; it is never shortened or ellipsized.
        fun sizeFor(v: String) = listOf(13f, 12f, 11f, 10f).firstOrNull { TextFit.fits(v, it, scale, availW - 24f) }
        // Even at 10 sp it would be cut: show the bare value ("7h 41m") instead, sized to fit.
        val value = if (sizeFor(m.value) == null) stripeValue(m.value) else m.value
        val vsp = sizeFor(value) ?: 10f
        val label = Layouts.rowLabel(m.label, value, availW, scale)
        MetricLine(label.orEmpty(), value, metricIcon(m.id), metricTint(m.id, p), label == null, GlanceModifier.height((20f * Layouts.ts(scale)).dp), valueSp = vsp)
    }
}

private const val MARGIN_SAFE = 4f

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
    if (!plan.side && plan.heroDp == 0) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(b.stepsText ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
            Spacer(GlanceModifier.height(4.dp))
            HBar((w - 2 * plan.pad - MARGIN_SAFE).dp, 6.dp, b.stepsFraction, skin.p.accent)
        }
        return
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (plan.heroDp == 0) {
                Column(GlanceModifier.width(((w - 2 * plan.pad) * 0.42f).dp)) {
                    Txt(b.stepsText ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
                    Spacer(GlanceModifier.height(4.dp))
                    HBar(((w - 2 * plan.pad) * 0.42f - MARGIN_SAFE).dp, 6.dp, b.stepsFraction, skin.p.accent)
                }
            } else StepsRing(b, plan)
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { Rows(shown, rows, false, true, w - 2 * plan.pad - (if (plan.heroDp == 0) (w - 2 * plan.pad) * 0.42f else plan.heroDp.toFloat()) - 16f) }
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
    val chips = (listOf(Chip(R.drawable.ic_w_steps, p.accent, b.stepsText ?: "–")) + shown.map { Chip(metricIcon(it.id), metricTint(it.id, p), stripeValue(it.value)) }).take(plan.count("c"))
    val (sw, _) = widgetSize()
    val chipW = (sw - 2f * plan.pad) / plan.perRow.coerceAtLeast(1) - 4f
    val scale = LocalFontScale
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        chips.chunked(plan.perRow).forEach { line ->
            Row(GlanceModifier.fillMaxWidth().height(Layouts.stripeRowDp(LocalFontScale).dp), verticalAlignment = Alignment.CenterVertically) {
                line.forEach { c ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(c.icon, 18.dp, c.tint)
                        Txt(c.text, if (TextFit.fits(c.text, 13f, scale, chipW)) 13f else 11f, FontWeight.Bold, maxLines = 1, align = TextAlign.Center)
                    }
                }
                repeat(plan.perRow - line.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

/** A stripe chip shows the bare value: "7 h 41 min · 82" becomes "7h 41m", "1,820 kcal" becomes "1,820". */
internal fun stripeValue(v: String): String = v.substringBefore(" \u00b7 ").removeSuffix(" kcal").replace(" h ", "h ").replace(" min", "m")
