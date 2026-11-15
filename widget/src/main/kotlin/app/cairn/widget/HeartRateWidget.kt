package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
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

/** Heart rate the COROS way: Resting HR is the main number, with its 7-day average, the daily average and min-max, in a heart/rose accent. */
class HeartRateWidget : CairnWidget<HeartRateUi>(WidgetKind.HeartRate) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<HeartRateUi>> = WidgetEnvironment.heartRate(unit)
    override fun sample() = Samples.heartRate()

    @Composable
    override fun Body(ui: WidgetUi.Content<HeartRateUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val p = skin.p
        val rose = p.stageRem
        val pair = cfg.style == WidgetStyle.HeartPair
        val heroText = if (pair) listOfNotNull(b.avgBpm?.let { Fmt.bpm(it) }, b.restingBpm?.let { Fmt.bpm(it) }).maxByOrNull { it.length } ?: b.hero else b.hero
        val plan = currentPlan(chars = heroText.length, heroText = heroText)
        val (w, _) = widgetSize()
        val lineW = if (plan.side) w - 2f * plan.pad - plan.heroAvailDp - 12f else w - 2f * plan.pad
        val avg7 = if (cfg.shows(Part.SevenDayAvg) && plan.has("avg7")) b.sevenDayText?.let { fitLine(listOf(it, it.removeSuffix(" bpm")), if (plan.side) plan.heroAvailDp else lineW, 11f) } else null
        val daily = if (cfg.shows(Part.DailyAvg) && plan.has("avgrow")) {
            val full = listOfNotNull(b.dailyText, b.minMaxText).joinToString(" · ")
            val short = listOfNotNull(b.avgBpm?.let { "Avg ${Fmt.bpm(it)}" }, b.minMaxText?.removeSuffix(" bpm")).joinToString(" · ")
            full.takeIf { it.isNotEmpty() }?.let { fitLine(listOf(it, short, b.avgBpm?.let { a -> "Avg ${Fmt.bpm(a)}" } ?: short), lineW, 11f) }
        } else null
        Framed(ui, kind, plan) {
            when (cfg.style) {
                WidgetStyle.HeartPair -> PairStyle(b, plan, avg7, if (cfg.shows(Part.DailyAvg) && plan.has("avgrow")) b.minMaxText?.let { "Range $it" } else null)
                else -> ChartStyle(b, plan, rose, avg7, daily, lineW, range = cfg.style == WidgetStyle.HeartRange)
            }
        }
    }
}

class HeartRateWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = HeartRateWidget()
}

@Composable
private fun ChartStyle(b: HeartRateUi, plan: Layouts.Plan, rose: Int, avg7: String?, daily: String?, lineW: Float, range: Boolean) {
    val p = skin.p
    val canRange = range && b.minBpm != null && b.maxBpm != null && b.avgBpm != null
    val spark = !range && b.series.size >= 2
    val chartOn = plan.has("chart") && (canRange || spark)
    val labels = if (plan.has("labels") && canRange) {
        fitLine(listOf("min ${b.minBpm} · avg ${b.avgBpm} · max ${b.maxBpm}", "${b.minBpm} · ${b.avgBpm} · ${b.maxBpm}", "${b.minBpm}-${b.maxBpm}"), lineW, 11f)
    } else null
    val heroBlock: @Composable () -> Unit = {
        if (plan.has("label")) Sub(b.label, 11f)
        BigValue(b.number, b.unit, plan.heroSp)
        avg7?.let { Sub(it, 11f) }
    }
    val chart: @Composable () -> Unit = {
        if (chartOn) {
            val cw = lineW.dp; val ch = plan.heroDp.dp
            if (canRange) {
                val axis = heartAxis(b.minBpm!!, b.maxBpm!!)
                ChartImage(Gfx.rangeBar(toPx(cw), toPx(ch), axis.fraction(b.minBpm) to axis.fraction(b.maxBpm), axis.fraction(b.avgBpm!!), p.track, softBand(rose), rose, p.start), cw, ch)
            } else {
                val f = sparkFractions(b.series)
                ChartImage(Gfx.trend(toPx(cw), toPx(ch), f, f.lastIndex, rose, rose, p.start), cw, ch)
            }
        }
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(plan.heroAvailDp.dp), verticalAlignment = Alignment.CenterVertically) { heroBlock() }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                chart()
                labels?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
                daily?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
            }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (plan.has("label")) Sub(b.label, 11f)
            BigValue(b.number, b.unit, plan.heroSp)
            if (canRange) {
                if (chartOn) { Spacer(GlanceModifier.height(6.dp)); chart() }
                labels?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 11f) }
                avg7?.let { Sub(it, 11f) }
            } else {
                avg7?.let { Sub(it, 11f) }
                if (chartOn) { Spacer(GlanceModifier.height(6.dp)); chart() }
                daily?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 11f) }
            }
        }
    }
}

/** Daily average and resting side by side, each with its own label. In a very narrow cell the unit moves out of the way before a value could be cut. */
@Composable
private fun PairStyle(b: HeartRateUi, plan: Layouts.Plan, avg7: String?, range: String?) {
    val half = plan.heroAvailDp
    val scale = LocalFontScale
    val numbers = listOf(b.avgBpm, b.restingBpm).map { it?.toString() ?: "\u2013" }
    val widest = numbers.maxBy { it.length }
    val unitFits = TextFit.fits("$widest bpm", plan.heroSp, scale, half, Layouts.HERO_CAP)
    val sp = plan.heroSp
    @Composable fun cell(labels: List<String>, number: String) {
        Sub(fitLine(labels, half, 11f), 11f)
        if (number == "\u2013") Txt(number, sp, FontWeight.Bold, hero = true) else BigValue(number, if (unitFits) "bpm" else "", sp)
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(GlanceModifier.defaultWeight()) { cell(listOf("Daily average", "Daily avg", "Avg"), numbers[0]) }
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) { cell(listOf("Resting", "Rest"), numbers[1]) }
        }
        avg7?.let { Spacer(GlanceModifier.height(4.dp)); Sub(it, 11f) }
        range?.let { Sub(it, 11f) }
        if (!unitFits) Sub("bpm", 11f)
    }
}
