package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

class FitnessWidget : CairnWidget<FitnessUi>(WidgetKind.Fitness) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<FitnessUi>> = WidgetEnvironment.fitness(unit)
    override fun sample() = Samples.fitness()

    @Composable
    override fun Body(ui: WidgetUi.Content<FitnessUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val preds = if (cfg.shows(Part.Predictions)) b.predictions else emptyList()
        val plan = currentPlan(heroText = b.vo2Text ?: "\u2013", values = preds.map { it.time })
        val thr = if (cfg.shows(Part.Threshold)) b.thresholdPace else null
        val unit = when {
            cfg.units == UnitsPref.Mi -> DistanceUnit.Mi
            cfg.units == UnitsPref.Km -> DistanceUnit.Km
            b.thresholdPace?.endsWith("/mi") == true -> DistanceUnit.Mi
            else -> DistanceUnit.Km
        }
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.FitnessVo2 -> Vo2Style(b, preds, thr, plan)
                WidgetStyle.FitnessLadder -> LadderStyle(b, preds, thr, plan, unit)
                else -> GridStyle(b, preds, thr, plan)
            }
        }
    }
}

class FitnessWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = FitnessWidget()
}

private fun longName(label: String) = when (label) { "HM" -> "Half marathon"; "M" -> "Marathon"; else -> label }

@Composable
private fun GridStyle(b: FitnessUi, preds: List<RacePrediction>, thr: String?, plan: Layouts.Plan) {
    val p = skin.p
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("head") || preds.isEmpty()) {
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Sub("VO2max", 12f)
                Spacer(GlanceModifier.width(6.dp))
                Txt(b.vo2Text ?: "–", 20f, FontWeight.Bold, hero = true)
                Spacer(GlanceModifier.defaultWeight())
                thr?.let { Sub(it, 11f) }
            }
        }
        if (preds.isEmpty()) Sub("No race predictions yet", 12f)
        if (preds.isNotEmpty()) {
            val tiles = preds.take(plan.count("p")).map { (if (TextFit.fits(longName(it.label), 10f, LocalFontScale, plan.heroAvailDp)) longName(it.label) else it.label) to it.time }
            TileRows(tiles, plan, p.inkSub)
        }
        if (plan.has("thr")) thr?.let { Spacer(GlanceModifier.height(2.dp)); MetricLine("Threshold pace", it, modifier = GlanceModifier.height(20.dp)) }
    }
}

@Composable
private fun Vo2Style(b: FitnessUi, preds: List<RacePrediction>, thr: String?, plan: Layouts.Plan) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("label")) Sub("VO2max", 12f)
        Txt(b.vo2Text ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
        if (plan.has("thr")) thr?.let { Sub("Threshold $it", 12f) }
        val scale = LocalFontScale
        val (w, _) = widgetSize()
        preds.take(plan.count("p")).forEach { r ->
            val room = w - 2f * plan.pad - TextFit.widthDp(r.time, 13f, scale) - 8f
            MetricLine(if (TextFit.fits(longName(r.label), 12f, scale, room)) longName(r.label) else r.label, r.time, modifier = GlanceModifier.height(18.dp))
        }
    }
}

@Composable
private fun LadderStyle(b: FitnessUi, preds: List<RacePrediction>, thr: String?, plan: Layouts.Plan, unit: DistanceUnit) {
    val p = skin.p
    data class Rung(val label: String, val time: String, val pace: String?)
    val rungs = buildList {
        thr?.let { add(Rung("Threshold", it, null)) }
        preds.forEach { add(Rung(longName(it.label), it.time, Layouts.predictionPace(it.label, it.time, unit))) }
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (rungs.isEmpty()) Sub("No pace data yet", 12f)
        rungs.take(plan.count("r")).forEachIndexed { i, r ->
            Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width((10 + i * 8).dp).height(8.dp).background(cp(p.accent)).cornerRadius(4.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                val scale = LocalFontScale
                val (w, _) = widgetSize()
                val left = w - 2f * plan.pad - (10 + i * 8) - 8f - TextFit.widthDp(r.time, 14f, scale) - 8f
                // Time is the value and always whole; the pace is shown only when it fits beside the label, the label shortens last.
                val showPace = r.pace != null && TextFit.fits(r.pace, 11f, scale, left - TextFit.widthDp(r.label.take(5), 12f, scale))
                Sub(if (TextFit.fits(r.label, 12f, scale, left - (if (showPace) TextFit.widthDp(r.pace!!, 11f, scale) + 8f else 0f))) r.label else r.label.take(5), 12f, modifier = GlanceModifier.defaultWeight())
                Txt(r.time, 14f, FontWeight.Bold, align = TextAlign.End)
                if (showPace) { Spacer(GlanceModifier.width(8.dp)); Sub(r.pace!!, 11f, align = TextAlign.End) }
            }
        }
    }
}
