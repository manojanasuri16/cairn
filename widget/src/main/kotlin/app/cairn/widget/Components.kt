package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.FitMath
import app.cairn.core.model.Freshness

// Glance composes RemoteViews and has no LocalConfiguration, so the font scale is read from its own LocalContext.
@Suppress("LocalContextConfigurationRead")
val LocalFontScale: Float @Composable get() = LocalContext.current.resources.configuration.fontScale

/** Text. Sizes follow the system font up to [Layouts.TEXT_CAP] (heroes: [Layouts.HERO_CAP]); beyond that the sp is reduced so the layout holds. */
@Composable
fun Txt(
    text: String,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Int? = null,
    maxLines: Int = 1,
    align: TextAlign? = null,
    modifier: GlanceModifier = GlanceModifier,
    hero: Boolean = false,
) = Text(
    text = text,
    modifier = modifier,
    style = TextStyle(
        color = cp(color ?: skin.p.ink),
        fontSize = SizeRules.cappedSp(size, LocalFontScale, if (hero) Layouts.HERO_CAP else Layouts.TEXT_CAP).sp,
        fontWeight = weight, textAlign = align,
    ),
    maxLines = maxLines,
)

@Composable
fun Sub(text: String, size: Float = 12f, maxLines: Int = 1, align: TextAlign? = null, modifier: GlanceModifier = GlanceModifier) =
    Txt(text, size, color = skin.p.inkSub, maxLines = maxLines, align = align, modifier = modifier)

/** Secondary text that steps down in size (never below 9 sp) before it would be cut off. */
@Composable
fun FitSub(text: String, size: Float, availW: Float, align: TextAlign? = null, modifier: GlanceModifier = GlanceModifier) =
    Sub(text, TextFit.fitSp(text, size, 9f, availW, LocalFontScale), align = align, modifier = modifier)

@Composable
fun bodyTap(context: Context, kind: WidgetKind): Action = actionStartActivity(bodyTapIntent(context, kind))

@Composable
fun tapIntent(context: Context, screen: Screen): Action = actionStartActivity(cairnIntent(context, screen))

/**
 * Shared surface: gradient / solid / transparent card, system (or extra round) corners clipped by the launcher-matched
 * radius, TalkBack summary, footer slot. The footer is always present.
 */
@Composable
fun WidgetFrame(
    description: String,
    bodyAction: Action,
    footer: @Composable () -> Unit,
    padding: Dp = 12.dp,
    body: @Composable () -> Unit,
) {
    val s = skin
    var mod = GlanceModifier.fillMaxSize().appWidgetBackground()
    mod = when (s.cfg.bg) {
        Bg.Gradient -> mod.background(ImageProvider(Gfx.background(s.p.start, s.p.center, s.p.end, s.cfg.opacity)), ContentScale.FillBounds)
        Bg.Solid -> mod.background(cp(Contrast.withAlpha(s.p.start, s.cfg.opacity / 100f)))
        Bg.Transparent -> mod
    }
    mod = if (s.cfg.corners == Corners.ExtraRound) mod.cornerRadius(32.dp) else mod.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    Column(modifier = mod.semantics { contentDescription = description }) {
        Box(GlanceModifier.defaultWeight().fillMaxWidth().padding(start = padding, end = padding, top = padding, bottom = 0.dp).clickable(bodyAction)) { body() }
        footer()
    }
}

@Composable
fun MessageView(m: WidgetUi.Message) {
    val context = LocalContext.current
    WidgetFrame(
        "${m.title}. ${m.hint}", tapIntent(context, m.screen),
        footer = { Spacer(GlanceModifier.size(10.dp)) }, padding = 14.dp,
    ) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(m.title, 16f, FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
            Sub(m.hint, 12f, maxLines = 3, align = TextAlign.Center)
        }
    }
}

@Composable
fun LoadingView() {
    val context = LocalContext.current
    WidgetFrame("Loading", tapIntent(context, Screen.Today), footer = { Spacer(GlanceModifier.size(10.dp)) }) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Sub("Loading…", 13f) }
    }
}

@Composable
private fun px(dp: Float): Int = (dp * LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(1)

/** Ring with content centred on it. */
@Composable
fun Ring(fraction: Float, size: Dp, stroke: Dp, color: Int, content: @Composable () -> Unit) {
    val p = skin.p
    val bmp = Gfx.ring(px(size.value), px(stroke.value), fraction, color, p.track)
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(size))
        content()
    }
}

/** Semi-circle gauge with content centred near the bottom of the arc. */
@Composable
fun Gauge(fraction: Float, width: Dp, stroke: Dp, color: Int, content: @Composable () -> Unit) {
    val p = skin.p
    val wPx = px(width.value); val sPx = px(stroke.value)
    val bmp = Gfx.gauge(wPx, sPx, fraction, color, p.track)
    val density = LocalContext.current.resources.displayMetrics.density
    val h = (Gfx.gaugeHeight(wPx, sPx) / density).dp
    Box(GlanceModifier.size(width, h), contentAlignment = Alignment.BottomCenter) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(width, h))
        content()
    }
}

@Composable
fun HBar(width: Dp, height: Dp, fraction: Float, color: Int) {
    Image(ImageProvider(Gfx.hbar(px(width.value), px(height.value), fraction, color, skin.p.track)), contentDescription = null, modifier = GlanceModifier.size(width, height))
}

/** Rounded tinted tile for grid variants: label above, value below. */
@Composable
fun Tile(label: String, value: String, modifier: GlanceModifier, valueSp: Float = 16f, accent: Int? = null, height: Dp? = null, padV: Dp = 4.dp) {
    val p = skin.p
    Column(
        (if (height != null) modifier.height(height) else modifier).background(cp(p.tile)).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, 10f, FontWeight.Medium, accent ?: p.inkSub)
        Txt(value, valueSp, FontWeight.Bold)
    }
}

/** Solid semantic pill with text in whichever of black or white reads best on it. */
@Composable
fun Pill(text: String, fill: Int, modifier: GlanceModifier = GlanceModifier, maxLines: Int = 2) {
    // In a narrow cell the word steps down to 10 sp before it wraps or is cut (the level word must always be readable).
    val (w, _) = widgetSize()
    val sp = TextFit.fitSp(text, 12f, 10f, w - 28f - 24f, LocalFontScale)
    Box(modifier.background(cp(fill)).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Txt(text, sp, FontWeight.Bold, Contrast.bestInk(listOf(fill)), maxLines = maxLines)
    }
}

fun metricIcon(id: MetricId): Int = when (id) {
    MetricId.Sleep -> R.drawable.ic_w_sleep
    MetricId.RestingHr -> R.drawable.ic_w_heart
    MetricId.Recovery -> R.drawable.ic_w_bolt
    MetricId.Calories -> R.drawable.ic_w_flame
    MetricId.Stress -> R.drawable.ic_w_stress
}

/** Frame with the footer wired up: compact when the user chose it or the cell is narrow. The footer is never omitted. */
@Composable
fun <T> Framed(ui: WidgetUi.Content<T>, kind: WidgetKind, plan: Layouts.Plan, body: @Composable () -> Unit) {
    val context = LocalContext.current
    val (w, _) = widgetSize()
    WidgetFrame(
        description = ui.description,
        bodyAction = bodyTap(context, kind),
        footer = { FooterView(ui.footer, ui.screen, compact = skin.cfg.compactFooter || w < 150f || skin.cfg.theme == app.cairn.core.designsystem.ThemeId.Clean) },
        padding = plan.pad.dp,
        body = body,
    )
}

/** Plan for the current cell, the style from the resolved config and the live font scale. */
@Composable
fun currentPlan(chars: Int = 6, heroText: String = "", values: List<String> = emptyList(), style: WidgetStyle = skin.cfg.style): Layouts.Plan {
    val (w, h) = widgetSize()
    return Layouts.plan(style, w, h, LocalFontScale, chars, heroText, values).also { PlanRecorder.sink?.invoke(skin.cfg.kind, it) }
}

/** Test hook (null in production): the widget matrix reads the layout plan each render used. */
object PlanRecorder {
    @Volatile var sink: ((WidgetKind, Layouts.Plan) -> Unit)? = null
}

/** Label left, value right, with an optional leading icon. */
@Composable
fun MetricLine(label: String, value: String, icon: Int? = null, iconTint: Int? = null, compact: Boolean = false, modifier: GlanceModifier = GlanceModifier, valueSp: Float = 13f) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, 14.dp, iconTint ?: skin.p.accent); Spacer(GlanceModifier.width(6.dp)) }
        if (!compact) Sub(label, 12f, modifier = GlanceModifier.defaultWeight()) else Spacer(GlanceModifier.defaultWeight())
        Txt(value, valueSp, FontWeight.Bold)
    }
}

/**
 * Tiles in rows that always span the full content width: each tile except the last is as wide as its text needs (plus the
 * shared spare width, split evenly), and the last absorbs whatever remains, so the row ends exactly at the right inset even
 * when the real cell is wider than the size the layout was chosen for. A lone tile spans the row. Labels shorten, never values.
 */
@Composable
fun TileRows(tiles: List<Pair<String, String>>, plan: Layouts.Plan, labelColor: Int? = null, compact: Boolean = false) {
    val (w, _) = widgetSize()
    val scale = LocalFontScale
    val inner = w - 2f * plan.pad
    var idx = 0
    FitMath.rows(tiles.size, plan.perRow).forEachIndexed { r, n ->
        val line = tiles.subList(idx, idx + n); idx += n
        val labels = line.map { (l, v) -> if (compact || TextFit.widthDp(l, 10f, scale) > TextFit.widthDp(v, plan.heroSp, scale)) Layouts.shortLabel(l) else l }
        val needs = line.mapIndexed { i, (_, v) -> maxOf(TextFit.widthDp(v, plan.heroSp, scale), TextFit.widthDp(labels[i], 10f, scale)) + Layouts.TILE_PAD }
        val widths = FitMath.balancedWidths(needs, inner, 4f)
        if (r > 0) Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            line.forEachIndexed { i, (_, v) ->
                val m = if (i == n - 1) GlanceModifier.defaultWeight() else GlanceModifier.width(widths[i].dp)
                Tile(labels[i], v, m, valueSp = plan.heroSp, accent = labelColor, height = plan.tileDp.dp, padV = if (compact) 2.dp else 4.dp)
                if (i < n - 1) Spacer(GlanceModifier.width(4.dp))
            }
        }
    }
}
