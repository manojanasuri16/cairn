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
import androidx.glance.text.TextAlign
import app.cairn.core.designsystem.Contrast
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/** Weekly mileage: every cached activity's distance from Monday to Sunday (the way COROS's month header sums), the count and the change against last week. */
class WeeklyMileageWidget : CairnWidget<MileageUi>(WidgetKind.WeeklyMileage) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<MileageUi>> = WidgetEnvironment.weeklyMileage(unit)
    override fun sample() = Samples.mileage()

    @Composable
    override fun Body(ui: WidgetUi.Content<MileageUi>) {
        val b = ui.body
        val cfg = skin.cfg
        val unit = if (b.unit == "mi") DistanceUnit.Mi else DistanceUnit.Km
        val goal = cfg.weeklyGoal
        // The Goal ring needs a goal: without one the widget shows the bars.
        val style = if (cfg.style == WidgetStyle.MileageGoal && goal == null) WidgetStyle.MileageBars else cfg.style
        val plan = currentPlan(chars = 4, heroText = b.hero, style = style)
        val (w, _) = widgetSize()
        val lineW = if (plan.side) w - 2f * plan.pad - plan.heroAvailDp - 12f else w - 2f * plan.pad
        val textW = if (plan.side) plan.heroAvailDp else w - 2f * plan.pad
        val count = if (cfg.shows(Part.ActivityCount) && plan.has("count")) b.countText else null
        val cmp = if (cfg.shows(Part.VsLastWeek) && plan.has("cmp")) b.comparison?.let { fitLine(listOf(it, it.replace("last week", "last wk"), it.substringBefore(" vs")), textW, 11f) } else null
        Framed(ui, kind, plan) {
            when (style) {
                WidgetStyle.MileageGoal -> GoalStyle(b, plan, unit, goal!!, count, cmp, textW)
                WidgetStyle.MileageTrend -> ChartStyle(b, plan, count, cmp, lineW, trend = true)
                else -> ChartStyle(b, plan, count, cmp, lineW, trend = false)
            }
        }
    }
}

class WeeklyMileageWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = WeeklyMileageWidget()
}

@Composable
private fun ChartStyle(b: MileageUi, plan: Layouts.Plan, count: String?, cmp: String?, chartW: Float, trend: Boolean) {
    val p = skin.p
    val hasChart = plan.has("chart") && (!trend || b.cumulative.size >= 2)
    val chart: @Composable () -> Unit = {
        if (hasChart) {
            val cw = chartW.dp; val ch = plan.heroDp.dp
            val todayIdx = b.bars.indexOfFirst { it.isToday }
            if (trend) ChartImage(Gfx.trend(toPx(cw), toPx(ch), b.cumulative, b.cumulative.lastIndex, p.accent, p.accent, p.start), cw, ch)
            else {
                ChartImage(Gfx.bars(toPx(cw), toPx(ch), b.bars.map { it.fraction }, todayIdx, p.accent, Contrast.withAlpha(p.accent, 0.55f)), cw, ch)
                Letters(b.bars.map { it.letter }, todayIdx, cw)
            }
        }
    }
    if (plan.side) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(plan.heroAvailDp.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plan.has("label")) Sub("Weekly mileage", 11f)
                BigValue(b.number, b.unit, plan.heroSp)
                count?.let { Sub(it, 12f) }
                cmp?.let { Sub(it, 11f) }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { chart() }
        }
    } else {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (plan.has("label")) Sub("Weekly mileage", 11f)
            BigValue(b.number, b.unit, plan.heroSp)
            if (trend) { cmp?.let { Sub(it, 11f) }; if (hasChart) { Spacer(GlanceModifier.height(6.dp)); chart() }; count?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 11f) } }
            else { count?.let { Sub(it, 11f) }; if (hasChart) { Spacer(GlanceModifier.height(6.dp)); chart() }; cmp?.let { Spacer(GlanceModifier.height(2.dp)); Sub(it, 11f) } }
        }
    }
}

@Composable
private fun GoalStyle(b: MileageUi, plan: Layouts.Plan, unit: DistanceUnit, goal: WeeklyGoal, count: String?, cmp: String?, textW: Float) {
    val p = skin.p
    val goalM = goal.meters
    val fraction = (b.totalMeters / goalM).toFloat().coerceIn(0f, 1f)
    val percent = (b.totalMeters / goalM * 100).roundToInt()
    val goalText = fitLine(listOf("of ${goal.inUnit(unit)} ${b.unit} goal", "of ${goal.inUnit(unit)} ${b.unit}"), textW, 11f)
    val percentText = "$percent%"
    val ringNumeral = Layouts.ringNumeralSp(plan.heroDp, Layouts.ringStroke(plan.heroDp), percentText.length).coerceAtMost(if (plan.side) 40f else 22f)
    val ring: @Composable () -> Unit = {
        Ring(fraction, plan.heroDp.dp, Layouts.ringStroke(plan.heroDp).dp, p.accent) { Txt(percentText, ringNumeral, FontWeight.Bold, hero = true) }
    }
    when {
        plan.heroDp == 0 -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BigValue(b.number, b.unit, plan.heroSp)
            if (plan.has("bar")) { Spacer(GlanceModifier.height(4.dp)); HBar(textW.dp, 8.dp, fraction, p.accent) }
            if (plan.has("goal")) Sub("$percentText ${goalText}", 11f)
        }
        plan.side -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ring()
            Spacer(GlanceModifier.width(16.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                BigValue(b.number, b.unit, plan.heroSp)
                if (plan.has("goal")) Sub(goalText, 12f)
                count?.let { Sub(it, 11f) }
                cmp?.let { Sub(it, 11f) }
            }
        }
        else -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            if (plan.has("total")) { Spacer(GlanceModifier.height(4.dp)); BigValue(b.number, b.unit, plan.heroSp) }
            if (plan.has("goal")) Sub(goalText, 11f, align = TextAlign.Center)
        }
    }
}
