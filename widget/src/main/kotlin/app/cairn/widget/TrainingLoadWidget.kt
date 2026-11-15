package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

class TrainingLoadWidget : CairnWidget<TrainingLoadUi>(WidgetKind.TrainingLoad) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<TrainingLoadUi>> = WidgetEnvironment.trainingLoad(unit)
    override fun sample() = Samples.load()

    @Composable
    override fun Body(ui: WidgetUi.Content<TrainingLoadUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val plan = currentPlan(chars = b.ratioText.length, heroText = b.ratioText)
        val color = skin.p.load(b.ratio)
        val vo2 = if (cfg.shows(Part.Vo2) && plan.has("vo2")) b.vo2Text?.let { it + (b.vo2Trend?.let { t -> " ($t)" } ?: "") } else null
        val comment = if (cfg.shows(Part.Comment) && plan.has("comment")) b.comment else null
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.LoadDial -> DialStyle(b, plan, color, comment, vo2)
                else -> ChartStyle(b, plan, color, comment, vo2, trend = cfg.style == WidgetStyle.LoadTrend)
            }
        }
    }
}

class TrainingLoadWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = TrainingLoadWidget()
}

@Composable
private fun dpPx(v: Dp): Int = (v.value * LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(1)

@Composable
private fun Chart(b: TrainingLoadUi, width: Dp, height: Dp, color: Int, trend: Boolean) {
    val p = skin.p
    val today = b.bars.indexOfFirst { it.isToday }
    val bmp = if (trend) Gfx.trend(dpPx(width), dpPx(height), b.bars.map { it.fraction }, today, color, color, p.ink)
    else Gfx.bars(dpPx(width), dpPx(height), b.bars.map { it.fraction }, today, color, Contrast.withAlpha(p.accent, 0.55f))
    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(width, height))
    Row(GlanceModifier.width(width)) {
        b.bars.forEach { Txt(it.letter, 9f, if (it.isToday) FontWeight.Bold else FontWeight.Normal, if (it.isToday) p.ink else p.inkSub, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight()) }
    }
}

@Composable
private fun ChartStyle(b: TrainingLoadUi, plan: Layouts.Plan, color: Int, comment: String?, vo2: String?, trend: Boolean) {
    val (w, _) = widgetSize()
    if (plan.side) {
        val left = maxOf((w * 0.34f).coerceIn(84f, 124f), (comment?.let { TextFit.widthDp(it, 12f, LocalFontScale) + 24f } ?: 0f).coerceAtMost(w * 0.45f))
        val chartW = (w - 2 * plan.pad - left - 12f).coerceAtLeast(60f)
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(left.dp), verticalAlignment = Alignment.CenterVertically) {
                Sub("Load ratio", 11f)
                Txt(b.ratioText, plan.heroSp, FontWeight.Bold, hero = true)
                comment?.let { Spacer(GlanceModifier.height(2.dp)); Pill(it, color) }
                vo2?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f, maxLines = 2) }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { Chart(b, chartW.dp, plan.heroDp.dp, color, trend) }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(b.ratioText, plan.heroSp, FontWeight.Bold, hero = true)
                comment?.let { Spacer(GlanceModifier.width(8.dp)); Pill(it, color) }
            }
            vo2?.let { Sub(it, 11f) }
            Spacer(GlanceModifier.height(6.dp))
            Chart(b, (w - 2 * plan.pad).dp, plan.heroDp.dp, color, trend)
        }
    }
}

@Composable
private fun DialStyle(b: TrainingLoadUi, plan: Layouts.Plan, color: Int, comment: String?, vo2: String?) {
    val p = skin.p
    val size = plan.heroDp.dp
    val bmp = Gfx.dial(dpPx(size), dpPx(10.dp), b.ratio, listOf(p.cool, p.good, p.warn, p.bad), color, p.ink)
    val dial: @Composable () -> Unit = {
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(size))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(b.ratioText, plan.heroSp, FontWeight.Bold, hero = true)
                if (plan.heroDp >= 88) Sub("ratio", 10f)
            }
        }
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            dial()
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                comment?.let { Pill(it, color); Spacer(GlanceModifier.height(4.dp)) }
                vo2?.let { Sub(it, 12f, maxLines = 2) }
                if (comment == null && vo2 == null) Sub("Load ratio", 12f)
            }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            dial()
            comment?.let { Txt(it, 12f, FontWeight.Bold, align = TextAlign.Center) }
            vo2?.let { Sub(it, 11f, align = TextAlign.Center) }
        }
    }
}
