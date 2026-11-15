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
import app.cairn.core.designsystem.Contrast
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

/** Overnight HRV: tonight's average, COROS's own assessment word as a coloured pill, the normal range and a range bar or seven nights. */
class HrvWidget : CairnWidget<HrvUi>(WidgetKind.Hrv) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<HrvUi>> = WidgetEnvironment.hrv(unit)
    override fun sample() = Samples.hrv()

    @Composable
    override fun Body(ui: WidgetUi.Content<HrvUi>) {
        val b = ui.body
        val cfg = skin.cfg
        // Without a range from the server the range bar has nothing to show: it becomes the number and pill.
        val style = if (cfg.style == WidgetStyle.HrvRange && (b.lowMs == null || b.highMs == null)) WidgetStyle.HrvPill else cfg.style
        val plan = currentPlan(chars = b.hero.length, heroText = b.hero, values = listOf(b.assessment), style = style)
        val (w, _) = widgetSize()
        val lineW = if (plan.side) w - 2f * plan.pad - plan.heroAvailDp - 12f else w - 2f * plan.pad
        val range = if (cfg.shows(Part.NormalRange) && plan.has("range")) b.rangeText?.let { fitLine(listOf(it, it.removePrefix("Normal range ")), lineW, 11f) } else null
        val base = if (cfg.shows(Part.Baseline) && plan.has("base")) b.baselineText?.let { fitLine(listOf(it, it.replace("Baseline", "Base")), lineW, 11f) } else null
        Framed(ui, kind, plan) {
            val color = skin.p.tone(b.tone)
            val hero: @Composable () -> Unit = {
                if (plan.has("label")) Sub("Overnight HRV", 11f)
                BigValue(b.number, b.unit, plan.heroSp)
                if (plan.has("pill")) { Spacer(GlanceModifier.height(4.dp)); Pill(b.assessment, color) }
            }
            val chart: @Composable (Float) -> Unit = { cw ->
                if (plan.has("chart")) when (style) {
                    WidgetStyle.HrvRange -> RangeChart(b, cw, plan.heroDp, color)
                    else -> NightsChart(b, cw, plan.heroDp, color)
                }
            }
            if (style == WidgetStyle.HrvPill) {
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    hero()
                    range?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 12f) }
                    base?.let { Sub(it, 11f) }
                }
            } else if (plan.side) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.width(plan.heroAvailDp.dp), verticalAlignment = Alignment.CenterVertically) { hero() }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        chart(lineW)
                        range?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
                        base?.let { Sub(it, 11f) }
                    }
                }
            } else {
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    hero()
                    if (plan.has("chart")) { Spacer(GlanceModifier.height(6.dp)); chart(lineW) }
                    range?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
                    base?.let { Sub(it, 11f) }
                }
            }
        }
    }
}

class HrvWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = HrvWidget()
}

/** The normal band shaded on a horizontal bar and a marker for tonight. */
@Composable
private fun RangeChart(b: HrvUi, width: Float, height: Int, color: Int) {
    val p = skin.p
    val axis = HrvAxis.bar(b)
    val band = axis.fraction(b.lowMs!!) to axis.fraction(b.highMs!!)
    val bmp = Gfx.rangeBar(toPx(width.dp), toPx(height.dp), band, axis.fraction(b.avgMs), p.track, softBand(p.good), color, p.start)
    ChartImage(bmp, width.dp, height.dp)
}

/** Seven nightly bars with the normal band as a shaded stripe behind them; tonight in the pill's colour. */
@Composable
private fun NightsChart(b: HrvUi, width: Float, height: Int, color: Int) {
    val p = skin.p
    val axis = HrvAxis.nights(b)
    val band = if (b.lowMs != null && b.highMs != null) axis.fraction(b.lowMs) to axis.fraction(b.highMs) else null
    val dim = Contrast.withAlpha(p.accent, 0.55f)
    val todayIdx = b.nights.indexOfFirst { it.isToday }
    val bmp = Gfx.bandBars(
        toPx(width.dp), toPx(height.dp), b.nights.map { it.ms?.let(axis::fraction) ?: -1f },
        b.nights.map { if (it.isToday) color else dim }, todayIdx, p.ink, band, softBand(p.good), p.track,
    )
    ChartImage(bmp, width.dp, height.dp)
    Letters(b.nights.map { it.letter }, todayIdx, width.dp)
}
