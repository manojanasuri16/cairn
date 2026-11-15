package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

/** Stress: today's average with its level word (an assumption, see docs/data-sources.md) as a pill, seven daily bars coloured by band, or a gauge. */
class StressWidget : CairnWidget<StressUi>(WidgetKind.Stress) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<StressUi>> = WidgetEnvironment.stress(unit)
    override fun sample() = Samples.stress()

    @Composable
    override fun Body(ui: WidgetUi.Content<StressUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val plan = currentPlan(chars = b.number.length, heroText = b.number, values = listOf(b.level))
        val (w, _) = widgetSize()
        val lineW = if (plan.side) w - 2f * plan.pad - plan.heroAvailDp - 12f else w - 2f * plan.pad
        val avg7 = if (cfg.shows(Part.SevenDayAvg) && plan.has("avg7")) b.sevenDayText?.let { fitLine(listOf(it, it.removePrefix("7-day ")), if (plan.side && cfg.style != WidgetStyle.StressBars) plan.heroAvailDp else lineW, 11f) } else null
        val color = skin.p.tone(b.tone)
        Framed(ui, kind, plan) {
            when {
                cfg.style == WidgetStyle.StressGauge && plan.heroDp > 0 -> GaugeStyle(b, plan, color, avg7)
                cfg.style == WidgetStyle.StressBars && plan.has("chart") -> BarsStyle(b, plan, color, avg7, lineW)
                else -> PillStyle(b, plan, color, avg7)
            }
        }
    }
}

class StressWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = StressWidget()
}

@Composable
private fun HeroBlock(b: StressUi, plan: Layouts.Plan, color: Int, avg7: String?) {
    if (plan.has("label")) Sub("Stress", 11f)
    BigValue(b.number, "", plan.heroSp)
    if (plan.has("pill")) { Spacer(GlanceModifier.height(4.dp)); Pill(b.level, color) }
    avg7?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
}

@Composable
private fun PillStyle(b: StressUi, plan: Layouts.Plan, color: Int, avg7: String?) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { HeroBlock(b, plan, color, avg7) }
}

@Composable
private fun BarsStyle(b: StressUi, plan: Layouts.Plan, color: Int, avg7: String?, chartW: Float) {
    val p = skin.p
    val todayIdx = b.bars.indexOfFirst { it.isToday }
    val chart: @Composable () -> Unit = {
        val bmp = Gfx.bandBars(
            toPx(chartW.dp), toPx(plan.heroDp.dp), b.bars.map { if (it.value == null) -1f else it.fraction },
            b.bars.map { p.tone(it.tone) }, todayIdx, p.ink, null, 0, p.track,
        )
        ChartImage(bmp, chartW.dp, plan.heroDp.dp)
        Letters(b.bars.map { it.letter }, todayIdx, chartW.dp)
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(plan.heroAvailDp.dp), verticalAlignment = Alignment.CenterVertically) { HeroBlock(b, plan, color, avg7) }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { chart() }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (plan.has("label")) Sub("Stress", 11f)
            BigValue(b.number, "", plan.heroSp)
            if (plan.has("pill")) { Spacer(GlanceModifier.height(4.dp)); Pill(b.level, color) }
            Spacer(GlanceModifier.height(6.dp)); chart()
            avg7?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 11f) }
        }
    }
}

@Composable
private fun GaugeStyle(b: StressUi, plan: Layouts.Plan, color: Int, avg7: String?) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge((b.value / 100f).coerceIn(0f, 1f), plan.heroDp.dp, 10.dp, color) { Txt(b.number, plan.heroSp, FontWeight.Bold, hero = true) }
        if (plan.has("level")) { Spacer(GlanceModifier.height(2.dp)); Txt(b.level, 13f, FontWeight.Bold, align = TextAlign.Center) }
        if (plan.has("avg7")) avg7?.let { Sub(it, 11f, align = TextAlign.Center) }
    }
}
