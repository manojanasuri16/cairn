package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import app.cairn.core.designsystem.Palette
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

class SleepWidget : CairnWidget<SleepUi>(WidgetKind.Sleep) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<SleepUi>> = WidgetEnvironment.sleep(unit)
    override fun sample() = Samples.sleep()

    @Composable
    override fun Body(ui: WidgetUi.Content<SleepUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val (w0, _) = widgetSize()
        val legendTexts = b.stages?.map { if (w0 >= 300f) it.text else Fmt.duration(it.minutes) } ?: emptyList()
        val plan = currentPlan(chars = (b.score?.toString() ?: b.durationText).length, heroText = b.durationText, values = legendTexts)
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.SleepRing -> RingStyle(b, plan)
                WidgetStyle.SleepTimeline -> TimelineStyle(b, plan)
                else -> BarStyle(b, plan)
            }
        }
    }
}

class SleepWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = SleepWidget()
}

fun stageColor(p: Palette, s: SleepStage): Int = when (s) {
    SleepStage.Deep -> p.stageDeep
    SleepStage.Light -> p.stageLight
    SleepStage.Rem -> p.stageRem
    SleepStage.Awake -> p.stageAwake
}

@Composable
private fun dpPx(v: Dp): Int = (v.value * LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(1)

@Composable
private fun StageBar(stages: List<StageSlice>, width: Dp) {
    val p = skin.p
    val bmp = Gfx.stageBar(dpPx(width), dpPx(8.dp), stages.map { it.minutes }, stages.map { stageColor(p, it.stage) })
    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(width, 8.dp))
}

@Composable
private fun Legend(stages: List<StageSlice>, withNames: Boolean) {
    val p = skin.p
    val (w, _) = widgetSize()
    val scale = LocalFontScale
    val texts = stages.map { if (withNames) it.text else Fmt.duration(it.minutes) }
    // Entries wrap onto more lines rather than cutting a duration.
    val lines = Layouts.legendLines(texts, w - 2f * Layouts.pad(app.cairn.core.designsystem.SizeClass.of(w, widgetSize().second), widgetSize().second), scale)
    val starts = lines.runningFold(0) { acc, line -> acc + line.size }
    Column(GlanceModifier.fillMaxWidth()) {
        lines.forEachIndexed { li, line ->
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (k in line.indices) {
                    val i = starts[li] + k
                    val s = stages[i]; val txt = texts[i]
                    Box(GlanceModifier.size(7.dp).background(cp(stageColor(p, s.stage))).cornerRadius(3.dp)) {}
                    Spacer(GlanceModifier.width(3.dp))
                    Txt(txt, 10f, color = p.inkSub)
                    Spacer(GlanceModifier.width(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BarStyle(b: SleepUi, plan: Layouts.Plan) {
    val (w, _) = widgetSize()
    val cfg = skin.cfg
    val stages = b.stages
    val barW = (w - 2 * plan.pad).dp
    val score = if (cfg.shows(Part.Score) && plan.has("score")) b.scoreText else null
    val window = if (cfg.shows(Part.Window) && plan.has("window")) b.window else null
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.side) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Txt(b.durationText, plan.heroSp, FontWeight.Bold, hero = true, modifier = GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    score?.let { Txt(it, 13f, FontWeight.Bold) }
                    window?.let { Sub(it, 12f) }
                }
            }
        } else {
            Sub("Sleep", 11f)
            Txt(b.durationText, plan.heroSp, FontWeight.Bold, hero = true)
            score?.let { Txt(it, 12f, FontWeight.Bold) }
        }
        if (stages != null && plan.has("bar")) {
            Spacer(GlanceModifier.height(6.dp))
            StageBar(stages, barW)
            if (plan.has("legend") && cfg.shows(Part.Stages)) {
                Spacer(GlanceModifier.height(4.dp))
                Legend(stages, withNames = w >= 300f)
                if (plan.has("note")) b.stageNote?.let { Sub(it, 10f) }
            }
        }
        if (!plan.side) window?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 12f) }
    }
}

@Composable
private fun RingStyle(b: SleepUi, plan: Layouts.Plan) {
    val p = skin.p
    val cfg = skin.cfg
    val fraction = ((b.score ?: (b.minutes * 100 / 480)) / 100f).coerceIn(0f, 1f)
    val numeral = b.score?.toString() ?: b.durationText
    val ring: @Composable () -> Unit = {
        Ring(fraction, plan.heroDp.dp, Layouts.ringStroke(plan.heroDp).dp, p.stageDeep) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(numeral, plan.heroSp, FontWeight.Bold, hero = true)
                if (plan.heroDp >= 80) Sub(if (b.score != null) "score" else "slept", 10f)
            }
        }
    }
    val window = if (cfg.shows(Part.Window)) b.window else null
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ring()
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (plan.has("dur")) Txt(b.durationText, 22f, FontWeight.Bold, hero = true)
                if (plan.has("window")) window?.let { Sub(it, 12f) }
                if (plan.has("legend") && cfg.shows(Part.Stages)) b.stages?.let { Spacer(GlanceModifier.height(4.dp)); Legend(it.take(3), false) }
            }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            if (plan.has("dur")) Txt(b.durationText, 13f, FontWeight.Bold, align = TextAlign.Center)
            if (plan.has("window")) window?.let { Sub(it, 11f, align = TextAlign.Center) }
        }
    }
}

@Composable
private fun TimelineStyle(b: SleepUi, plan: Layouts.Plan) {
    val p = skin.p
    val (w, _) = widgetSize()
    val cfg = skin.cfg
    val stages = (b.stages ?: emptyList()).sortedBy { it.stage.ordinal }
    val total = stages.sumOf { it.minutes }.coerceAtLeast(1)
    val scale = LocalFontScale
    val labelW = maxOf(44f, stages.maxOfOrNull { TextFit.widthDp(it.stage.label, 11f, scale) + 4f } ?: 0f)
    val valueW = maxOf(48f, stages.maxOfOrNull { TextFit.widthDp(Fmt.duration(it.minutes), 11f, scale) + 4f } ?: 0f)
    val barW = (w - 2 * plan.pad - labelW - valueW - 8f).coerceAtLeast(40f)
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Txt(b.durationText, plan.heroSp, FontWeight.Bold, hero = true, modifier = GlanceModifier.defaultWeight())
            if (w >= 200f && cfg.shows(Part.Score)) b.scoreText?.let { Txt(it, 12f, FontWeight.Bold) }
        }
        if (plan.has("axis") && cfg.shows(Part.Window)) b.window?.let { Sub(it, 11f) }
        if (cfg.shows(Part.Stages)) stages.take(plan.count("l")).forEach { s ->
            Row(GlanceModifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(s.stage.label, 11f, color = p.inkSub, modifier = GlanceModifier.width(labelW.dp))
                HBarColor(barW.dp, 8.dp, s.minutes / total.toFloat(), stageColor(p, s.stage))
                Spacer(GlanceModifier.width(8.dp))
                Txt(Fmt.duration(s.minutes), 11f, FontWeight.Medium, modifier = GlanceModifier.width(valueW.dp))
            }
        }
    }
}

@Composable
private fun HBarColor(width: Dp, height: Dp, fraction: Float, color: Int) = HBar(width, height, fraction.coerceAtLeast(0.04f), color)
