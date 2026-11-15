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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.FitMath
import app.cairn.core.designsystem.Palette
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

/**
 * A grid of 1 to 6 user-chosen stats that fills the box exactly (every row and column is an equal weighted share, so there is
 * no slack): one hero, two side by side, one big and two small, a 2x2, or 3x2 / 2x3 by aspect ratio. Each tile has a small header
 * (icon and the stat's name in its own colour), a bold number with its unit beside it on one baseline, and, where there is room,
 * a progress ring or 7-day trend in the same colour.
 */
class OverviewWidget : CairnWidget<OverviewUi>(WidgetKind.Overview) {
    /**
     * Exact, not Responsive: with a size map the host picks one layout for every reported size (portrait min width x max height AND
     * landscape max width x min height), so a short landscape entry such as 361x125 beat the portrait 176x223 and a 4-stat grid came
     * out as 2 tiles. Exact reads the size for the current orientation only. Previews still use the size list in [WidgetSizes].
     */
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    override fun flow(unit: DistanceUnit?): Flow<WidgetUi<OverviewUi>> = WidgetEnvironment.overview(unit)
    override fun sample() = Samples.overview()

    @Composable
    override fun Body(ui: WidgetUi.Content<OverviewUi>) {
        val cfg = skin.cfg
        val plan = currentPlan()
        val (w, h) = widgetSize()
        val inner = w - 2f * plan.pad
        // Height available to the grid: the cell minus padding and the quiet footer.
        val innerH = Layouts.availDp(w, h, LocalFontScale)
        val chosen = cfg.metrics
        val op = OverviewLayout.plan(chosen.size, inner, innerH)
        val tiles = OverviewLayout.firstN(chosen, op.shown).map { id -> ui.body.find(id) ?: OverviewItem(id, "–") }
        WidgetLog.d("overview LocalSize=${w}x$h dp inner=${inner}x$innerH chosen=${chosen.map { it.name }} plan[shown=${op.shown} pattern=${op.pattern} cols=${op.cols} rows=${op.rows} tile=${op.tileW}x${op.tileH}] emitting=${tiles.size} tiles=${tiles.map { it.id.name }} available=${ui.body.items.map { it.id.name }}")
        Framed(ui, kind, plan) { Grid(tiles, op, cfg.shows(Part.Rings)) }
    }
}

class OverviewWidgetReceiver : CairnReceiver() {
    override val glanceAppWidget = OverviewWidget()
}

/** Each stat's own colour (Apple-style titles): steps orange, sleep indigo, recovery green, heart rose, calories amber. */
fun metricAccent(p: Palette, item: OverviewItem): Int = when (item.id) {
    OverviewId.Steps -> p.stageAwake
    OverviewId.Sleep, OverviewId.SleepScore -> p.stageDeep
    OverviewId.Recovery -> p.good
    OverviewId.RestingHr -> p.stageRem
    OverviewId.Calories -> p.warn
    OverviewId.Stress -> p.cool
    OverviewId.LoadRatio -> p.load(item.level)
    OverviewId.Vo2 -> p.stageLight
    OverviewId.LastDistance, OverviewId.TodayRun -> p.accent
    OverviewId.HeartRate -> p.stageRem
    OverviewId.Hrv -> p.good
    OverviewId.WeeklyLoad -> p.warn
}

/** The same colour at 4.5:1 for small header text. */
fun metricTitleColor(p: Palette, item: OverviewItem): Int = Contrast.ensure(metricAccent(p, item), p.textStops + p.tile, 4.5)

fun metricIconFor(id: OverviewId): Int = when (id) {
    OverviewId.Steps, OverviewId.LastDistance, OverviewId.TodayRun -> R.drawable.ic_w_steps
    OverviewId.Sleep, OverviewId.SleepScore -> R.drawable.ic_w_sleep
    OverviewId.Recovery -> R.drawable.ic_w_bolt
    OverviewId.RestingHr, OverviewId.HeartRate, OverviewId.Hrv -> R.drawable.ic_w_heart
    OverviewId.Calories -> R.drawable.ic_w_flame
    OverviewId.Stress -> R.drawable.ic_w_stress
    OverviewId.LoadRatio, OverviewId.Vo2, OverviewId.WeeklyLoad -> R.drawable.ic_w_chart
}

@Composable
private fun Grid(tiles: List<OverviewItem>, plan: OverviewPlan, rings: Boolean) {
    val g = OverviewLayout.GAP.dp
    @Composable fun tile(i: Int, m: GlanceModifier) = Tile(tiles[i], plan, rings, m)
    when (plan.pattern) {
        OverviewPattern.Single -> Hero(tiles[0], plan, rings)
        OverviewPattern.BigLeft -> Row(GlanceModifier.fillMaxSize()) {
            tile(0, GlanceModifier.defaultWeight().fillMaxSize())
            Spacer(GlanceModifier.width(g))
            Column(GlanceModifier.defaultWeight().fillMaxSize()) {
                tile(1, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(g))
                tile(2, GlanceModifier.defaultWeight().fillMaxWidth())
            }
        }
        OverviewPattern.BigTop -> Column(GlanceModifier.fillMaxSize()) {
            tile(0, GlanceModifier.defaultWeight().fillMaxWidth())
            Spacer(GlanceModifier.height(g))
            Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                tile(1, GlanceModifier.defaultWeight().fillMaxSize())
                Spacer(GlanceModifier.width(g))
                tile(2, GlanceModifier.defaultWeight().fillMaxSize())
            }
        }
        OverviewPattern.Grid -> {
            // Row slices are computed up front: nothing is mutated inside the composable lambdas, so a recomposition can never skip cells.
            val perRow = FitMath.rows(tiles.size, plan.cols)
            val starts = perRow.runningFold(0) { acc, n -> acc + n }
            Column(GlanceModifier.fillMaxSize()) {
                perRow.forEachIndexed { r, n ->
                    if (r > 0) Spacer(GlanceModifier.height(g))
                    Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                        for (i in starts[r] until starts[r] + n) {
                            if (i > starts[r]) Spacer(GlanceModifier.width(g))
                            tile(i, GlanceModifier.defaultWeight().fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(item: OverviewItem, plan: OverviewPlan, rings: Boolean, modifier: GlanceModifier) {
    val p = skin.p
    val scale = LocalFontScale
    val accent = metricAccent(p, item)
    val title = metricTitleColor(p, item)
    val pad = OverviewLayout.PAD
    val single = plan.pattern == OverviewPattern.Single
    // Tile size is the smallest tile of the pattern (conservative); the weighted cell may be a little larger, never smaller.
    val tw = if (single) plan.tileW else plan.tileW
    val th = plan.tileH
    val ring = if (rings && item.fraction != null) OverviewLayout.ringSize(item.number, item.unit, tw, th, scale) else 0f
    val trendRoom = rings && item.trend.size >= 2 && item.fraction == null && th >= 70f && tw >= 110f
    val (sp, unitSp) = OverviewLayout.valueSizes(item.number, item.unit, tw, th, scale, if (ring > 0f) ring + 6f else 0f)
    Row(
        modifier.background(cp(subtleTint(p))).cornerRadius(16.dp).padding(pad.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Header directly above the value, the pair centred in the tile: no stretched gap between them.
        Column(GlanceModifier.defaultWeight().fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(metricIconFor(item.id), 12.dp, accent)
                Spacer(GlanceModifier.width(4.dp))
                Txt(item.id.label, 10f, FontWeight.Medium, title, maxLines = 1)
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(item.number, sp, FontWeight.Bold, hero = true)
                if (item.unit.isNotEmpty()) { Spacer(GlanceModifier.width(2.dp)); Txt(item.unit, unitSp, FontWeight.Normal, p.inkSub) }
            }
            if (trendRoom) {
                val d = LocalContext.current.resources.displayMetrics.density
                val bw = (tw - 2 * pad).coerceAtLeast(40f); val bh = 18f
                Image(ImageProvider(Gfx.trend((bw * d).toInt(), (bh * d).toInt(), item.trend, item.trend.lastIndex, accent, accent, p.tile)), contentDescription = null, modifier = GlanceModifier.size(bw.dp, bh.dp))
            }
        }
        if (ring > 0f) {
            Spacer(GlanceModifier.width(6.dp))
            Ring(item.fraction ?: 0f, ring.dp, (ring / 7f).coerceIn(4f, 9f).dp, accent) {}
        }
    }
}

/** A very light tonal tint (about 5% ink over the card), so tiles read as part of the card, not as grey boxes inside it. */
fun subtleTint(p: Palette): Int = Contrast.blend(p.ink, p.start, 0.05f)

/**
 * One stat fills the box directly on the widget surface (no card inside the card): header top-left, the big number with its
 * unit, and a ring (steps, recovery, sleep) or 7-day trend in the stat's colour taking the middle space.
 */
@Composable
private fun Hero(item: OverviewItem, plan: OverviewPlan, rings: Boolean) {
    val p = skin.p
    val scale = LocalFontScale
    val accent = metricAccent(p, item)
    val tw = plan.tileW; val th = plan.tileH
    val graphic = (th - 24f).coerceAtLeast(0f)
    val ring = if (rings && item.fraction != null) minOf(graphic, tw * 0.46f).takeIf { it >= 40f } ?: 0f else 0f
    val trendW = if (rings && ring == 0f && item.trend.size >= 2) minOf(tw * 0.42f, 160f) else 0f
    val reserve = maxOf(ring, trendW).let { if (it > 0f) it + 8f else 0f }
    val (sp, unitSp) = OverviewLayout.valueSizes(item.number, item.unit, tw, th, scale, reserve, maxSp = 72f)
    Column(GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(metricIconFor(item.id), 14.dp, accent)
            Spacer(GlanceModifier.width(6.dp))
            Txt(item.id.label, 12f, FontWeight.Medium, metricTitleColor(p, item), maxLines = 1)
        }
        Row(GlanceModifier.defaultWeight().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Bottom) {
                Txt(item.number, sp, FontWeight.Bold, hero = true)
                if (item.unit.isNotEmpty()) { Spacer(GlanceModifier.width(3.dp)); Txt(item.unit, unitSp, FontWeight.Normal, p.inkSub) }
            }
            if (ring > 0f) Ring(item.fraction ?: 0f, ring.dp, (ring / 7f).coerceIn(5f, 12f).dp, accent) {}
            else if (trendW > 0f) {
                val d = LocalContext.current.resources.displayMetrics.density
                val bh = minOf(graphic, 64f)
                Image(ImageProvider(Gfx.trend((trendW * d).toInt(), (bh * d).toInt(), item.trend, item.trend.lastIndex, accent, accent, p.start)), contentDescription = null, modifier = GlanceModifier.size(trendW.dp, bh.dp))
            }
        }
    }
}
