package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

class LastActivityWidget : CairnWidget<ActivityUi>(WidgetKind.LastActivity) {
    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<ActivityUi>> = WidgetEnvironment.lastActivity(unit)
    override fun sample() = Samples.activity()

    @Composable
    override fun Body(ui: WidgetUi.Content<ActivityUi>) {
        val b = ui.body
        val cfg = skin.cfg
        // Priority: duration (when distance is the hero), pace, average heart rate; the user can hide each.
        val stats = listOfNotNull(
            b.duration?.takeIf { cfg.shows(Part.Duration) }?.let { "Time" to it },
            b.pace?.takeIf { cfg.shows(Part.Pace) }?.let { "Pace" to it },
            b.avgHr?.takeIf { cfg.shows(Part.AvgHr) }?.let { "Heart rate" to it },
            b.calories?.let { "Calories" to it },
        )
        val values = listOfNotNull(b.distance) + stats.map { it.second }
        val (w0, h0) = widgetSize()
        val inner0 = w0 - 2f * Layouts.pad(app.cairn.core.designsystem.SizeClass.of(w0, h0), h0)
        val lineStyle = Layouts.effectiveStyle(cfg.style, w0, h0) == WidgetStyle.ActivityLine
        // One-liner text: the hero value first, then every available stat that fits the line at a legible size.
        val rest = if (b.distance != null) stats.map { it.second } else stats.filter { it.first != "Time" }.map { it.second }
        val allParts = listOfNotNull(b.distance ?: b.duration) + rest
        val twoParts = Layouts.activityMain(allParts.take(2), inner0, LocalFontScale)
        @Composable fun planFor(lineText: String) = currentPlan(heroText = if (cfg.style == WidgetStyle.ActivityGrid && !lineStyle) "" else if (lineStyle) lineText else (b.distance ?: b.duration ?: "\u2013"), values = values)
        var plan = planFor(twoParts)
        var lineText = twoParts
        // With no room for a second line, the one line carries every stat that fits.
        if (lineStyle && !plan.has("meta")) { lineText = Layouts.activityMain(allParts, inner0, LocalFontScale); plan = planFor(lineText) }
        Framed(ui, kind, plan) {
            when (plan.style) {
                WidgetStyle.ActivityGrid -> GridStyle(b, stats, plan)
                WidgetStyle.ActivityLine -> LineStyle(b, rest, plan, lineText)
                else -> HeroStyle(b, stats, plan)
            }
        }
    }
}

class LastActivityWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = LastActivityWidget()
}

private fun shortLabel(l: String) = when (l) { "Distance" -> "Dist"; "Heart rate" -> "HR"; "Calories" -> "kcal"; else -> l }

@Composable
private fun Header(b: ActivityUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.height(8.dp).width(8.dp).background(cp(skin.p.accent)).cornerRadius(4.dp)) {}
        Spacer(GlanceModifier.width(6.dp))
        Txt("${b.sport} · ${b.date}", 12f, FontWeight.Medium, skin.p.inkSub)
    }
}

@Composable
private fun HeroStyle(b: ActivityUi, stats: List<Pair<String, String>>, plan: Layouts.Plan) {
    val shown = if (b.distance != null) stats else stats.filter { it.first != "Time" }
    val rows = plan.count("s")
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("header")) Header(b)
        Txt(b.distance ?: b.duration ?: "–", plan.heroSp, FontWeight.Bold, hero = true)
        shown.take(rows * plan.perRow).chunked(plan.perRow).forEach { line ->
            Row(GlanceModifier.fillMaxWidth()) { line.forEach { Txt(it.second, if (plan.perRow >= 3) 11f else 13f, FontWeight.Medium, modifier = GlanceModifier.defaultWeight()) } }
        }
    }
}

@Composable
private fun GridStyle(b: ActivityUi, stats: List<Pair<String, String>>, plan: Layouts.Plan) {
    val all = listOfNotNull(b.distance?.let { "Distance" to it }) + stats
    val tiles = all.take(plan.count("t")).map { (l, v) -> (if (TextFit.fits(l, 10f, LocalFontScale, plan.heroAvailDp)) l else shortLabel(l)) to v }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("header")) { Header(b); Spacer(GlanceModifier.height(4.dp)) }
        TileRows(tiles, plan, compact = !plan.has("header") && widgetSize().second < 90f)
    }
}

@Composable
private fun LineStyle(b: ActivityUi, rest: List<String>, plan: Layouts.Plan, main: String) {
    // The second line holds whatever the first did not.
    val meta = rest.drop(1).joinToString(" \u00b7 ")
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.has("title")) Header(b)
        Txt(main, plan.heroSp, FontWeight.Bold, hero = true)
        if (plan.has("meta") && meta.isNotEmpty()) Sub(meta, 13f)
    }
}
