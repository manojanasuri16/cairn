package app.cairn.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.Palette
import app.cairn.core.designsystem.Palettes

/** Palette plus the user's choices, resolved once per composition. */
data class Skin(val p: Palette, val cfg: ResolvedConfig)

val LocalSkin = staticCompositionLocalOf<Skin> { error("CairnGlanceTheme missing") }

fun isDarkMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** The palette a widget draws with. See-through cards use ink sized for a wallpaper, never the theme's card ink. */
fun skinFor(context: Context, cfg: ResolvedConfig): Skin {
    val dark = isDarkMode(context)
    val themed = Palettes.fixed(cfg.theme, dark, cfg.accent)
    val p = if (cfg.onWallpaper) Palettes.onWallpaper(dark, cfg.accent, themed.accent) else themed
    return Skin(p, cfg)
}

@Composable
fun CairnGlanceTheme(cfg: ResolvedConfig, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val skin = remember(cfg, isDarkMode(context)) { skinFor(context, cfg) }
    CompositionLocalProvider(LocalSkin provides skin) { content() }
}

val skin: Skin @Composable get() = LocalSkin.current

fun cp(argb: Int): ColorProvider = ColorProvider(Color(argb))

@Composable
fun Icon(res: Int, size: Dp, tint: Int) =
    Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = ColorFilter.tint(cp(tint)))

/** The widget's size as Glance reports it (exact), in dp. */
@Composable
fun widgetSize(): Pair<Float, Float> = LocalSize.current.let { it.width.value + WidgetSizes.MARGIN_DP to it.height.value + WidgetSizes.MARGIN_DP }

fun Palette.withAlpha(argb: Int, a: Float) = Contrast.withAlpha(argb, a)
