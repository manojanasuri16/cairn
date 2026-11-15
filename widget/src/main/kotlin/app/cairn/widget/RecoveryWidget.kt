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
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

class RecoveryWidget : CairnWidget<RecoveryUi>(WidgetKind.Recovery) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<RecoveryUi>> = WidgetEnvironment.recovery(unit)
    override fun sample() = Samples.recovery()

    @Composable
    override fun Body(ui: WidgetUi.Content<RecoveryUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val plan = currentPlan(chars = b.percentText.length, heroText = b.percentText)
        val color = skin.p.recovery((b.fraction * 100).roundToInt())
        val ttf = if (cfg.shows(Part.TimeToFull) && plan.has("ttf")) b.timeToFull else null
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.RecoveryGauge -> GaugeStyle(b, plan, color, ttf)
                WidgetStyle.RecoveryPill -> PillStyle(b, plan, color, ttf)
                else -> RingStyle(b, plan, color, ttf)
            }
        }
    }
}

class RecoveryWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = RecoveryWidget()
}

@Composable
private fun RingStyle(b: RecoveryUi, plan: Layouts.Plan, color: Int, ttf: String?) {
    val ring: @Composable () -> Unit = {
        Ring(b.fraction, plan.heroDp.dp, Layouts.ringStroke(plan.heroDp).dp, color) { Txt(b.percentText, plan.heroSp, FontWeight.Bold, hero = true) }
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ring()
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (plan.has("level")) Txt(b.level, 16f, FontWeight.Bold, maxLines = 2)
                ttf?.let { Sub(it, 12f, maxLines = 2) }
            }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            if (plan.has("level")) Txt(b.level, 11f, FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
            ttf?.let { Sub(it, 11f, align = TextAlign.Center) }
        }
    }
}

@Composable
private fun GaugeStyle(b: RecoveryUi, plan: Layouts.Plan, color: Int, ttf: String?) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge(b.fraction, plan.heroDp.dp, 10.dp, color) { Txt(b.percentText, plan.heroSp, FontWeight.Bold, hero = true) }
        if (plan.has("level")) { Spacer(GlanceModifier.height(2.dp)); Txt(b.level, 13f, FontWeight.Bold, align = TextAlign.Center) }
        ttf?.let { Sub(it, 11f, align = TextAlign.Center) }
    }
}

@Composable
private fun PillStyle(b: RecoveryUi, plan: Layouts.Plan, color: Int, ttf: String?) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("label")) Sub("Recovery", 12f)
        Txt(b.percentText, plan.heroSp, FontWeight.Bold, hero = true)
        if (plan.has("pill")) { Spacer(GlanceModifier.height(4.dp)); Pill(b.level, color) }
        ttf?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 12f, maxLines = 1) }
    }
}
