package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.cairn.core.model.Freshness

@Composable
fun Txt(
    text: String,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    maxLines: Int = 1,
    align: TextAlign? = null,
    modifier: GlanceModifier = GlanceModifier,
) = Text(
    text = text,
    modifier = modifier,
    style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = align),
    maxLines = maxLines,
)

val LocalFontScale: Float @Composable get() = fontScaleOf(LocalContext.current)

/** Glance recomposes on configuration change, so reading the scale at composition time is fine. */
private fun fontScaleOf(context: Context): Float = context.resources.configuration.fontScale

@Composable
fun tapIntent(context: Context, screen: Screen): Action = actionStartActivity(cairnIntent(context, screen))

/** Body tap: the user's chosen COROS screen for this widget (see Settings, Widget tap targets). */
@Composable
fun bodyTap(context: Context, kind: WidgetKind): Action = actionStartActivity(bodyTapIntent(context, kind))

/** Shared surface: tonal background, system corner radius, TalkBack summary, footer slot. */
@Composable
fun WidgetFrame(
    description: String,
    bodyAction: Action,
    footer: (@Composable () -> Unit)? = null,
    padding: Dp = 12.dp,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .semantics { contentDescription = description },
    ) {
        Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(start = padding, end = padding, top = padding, bottom = if (footer == null) padding else 2.dp).clickable(bodyAction)) { body() }
        if (footer != null) footer()
    }
}

/** The one footer all widgets share: age text, tier-styled, tap opens COROS when stale. */
@Composable
fun FooterView(footer: FooterUi, screen: Screen, compact: Boolean = false) {
    val context = LocalContext.current
    val stale = footer.tier == Freshness.Stale
    val color = when (footer.tier) {
        Freshness.Fresh -> GlanceTheme.colors.onSurface
        Freshness.Aging -> GlanceTheme.colors.onSurfaceVariant
        Freshness.Stale -> GlanceTheme.colors.error
    }
    Box(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = if (compact) 6.dp else 8.dp)
            .clickable(actionStartActivity(footerIntent(context, footer, screen))),
    ) {
        Txt(footer.text, if (compact) 10f else 11f, if (stale) FontWeight.Bold else FontWeight.Normal, color)
    }
}

@Composable
fun MessageView(m: WidgetUi.Message) {
    val context = LocalContext.current
    WidgetFrame("${m.title}. ${m.hint}", tapIntent(context, m.screen)) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(m.title, 16f, FontWeight.Medium, maxLines = 2, align = TextAlign.Center)
            Txt(m.hint, 12f, color = GlanceTheme.colors.onSurfaceVariant, maxLines = 3, align = TextAlign.Center)
        }
    }
}

@Composable
fun LoadingView() {
    val context = LocalContext.current
    WidgetFrame("Loading", tapIntent(context, Screen.Today)) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("Loading…", 13f, color = GlanceTheme.colors.onSurfaceVariant)
        }
    }
}

/** Ring with content centred on it. The bitmap is drawn at the device's exact pixel size. */
@Composable
fun Ring(fraction: Float, size: Dp, stroke: Dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bmp = RingBitmap.get(
        sizePx = (size.value * density).toInt().coerceAtLeast(1),
        strokePx = (stroke.value * density).toInt().coerceAtLeast(1),
        fraction = fraction,
        progressColor = GlanceTheme.colors.primary.getColor(context).toArgb(),
        trackColor = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb(),
    )
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(size))
        content()
    }
}
