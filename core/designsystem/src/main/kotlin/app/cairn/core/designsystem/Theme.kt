package app.cairn.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tabular figures everywhere so numbers never shift width; tight tracking on big numerals, slightly open on small labels. */
fun cairnTypography(base: Typography = Typography()): Typography {
    fun TextStyle.tnum(tracking: Float? = null) =
        copy(fontFeatureSettings = "tnum", letterSpacing = tracking?.sp ?: letterSpacing)
    return base.copy(
        displayLarge = base.displayLarge.tnum(-1f),
        displayMedium = base.displayMedium.tnum(-0.75f).copy(fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.tnum(-0.5f).copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.tnum(-0.25f),
        headlineMedium = base.headlineMedium.tnum(-0.25f),
        headlineSmall = base.headlineSmall.tnum(),
        titleLarge = base.titleLarge.tnum(),
        titleMedium = base.titleMedium.tnum(),
        titleSmall = base.titleSmall.tnum(),
        bodyLarge = base.bodyLarge.tnum(),
        bodyMedium = base.bodyMedium.tnum(),
        bodySmall = base.bodySmall.tnum(),
        labelLarge = base.labelLarge.tnum(0.1f),
        labelMedium = base.labelMedium.tnum(0.4f),
        labelSmall = base.labelSmall.tnum(0.5f),
    )
}

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark"); companion object { fun parse(raw: String?) = entries.firstOrNull { it.name == raw } ?: System } }

/** The look chosen in Settings. [dynamic] swaps the accent for Material You's colours. */
data class AppLook(val accent: Accent = Accent.DEFAULT, val mode: ThemeMode = ThemeMode.System, val dynamic: Boolean = false)

/** Hero-card palette (white text on the accent gradient) for the current look. */
val LocalCairnPalette = staticCompositionLocalOf { AppColors.hero(Accent.DEFAULT, false) }

/** Semantic colours for neutral cards (recovery level, load range, sleep stages, data age). */
val LocalCardColors = staticCompositionLocalOf { AppColors.card(false) }

private fun Int.c() = Color(this)

private fun scheme(a: Accent, dark: Boolean): ColorScheme {
    val r = AppColors.roles(a, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = r.primary.c(), onPrimary = r.onPrimary.c(), primaryContainer = r.container.c(), onPrimaryContainer = r.onContainer.c(),
        secondary = r.primary.c(), onSecondary = r.onPrimary.c(), secondaryContainer = r.container.c(), onSecondaryContainer = r.onContainer.c(),
        tertiary = r.primary.c(),
        background = r.background.c(), onBackground = r.onSurface.c(), surface = r.background.c(), onSurface = r.onSurface.c(),
        surfaceVariant = r.surfaceVariant.c(), onSurfaceVariant = r.onSurfaceVariant.c(),
        surfaceContainerLowest = r.containerLowest.c(), surfaceContainerLow = r.containerLow.c(), surfaceContainer = r.card.c(),
        surfaceContainerHigh = r.containerHigh.c(), surfaceContainerHighest = r.containerHighest.c(),
        outline = r.outline.c(), outlineVariant = r.outlineVariant.c(), error = r.error.c(),
    )
}

/** Neutral surfaces for every accent; the accent only colours buttons, selection, rings and the one hero card. */
@Composable
fun CairnTheme(look: AppLook = AppLook(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (look.mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val scheme = remember(look, dark, context) {
        if (look.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }.getOrElse { scheme(look.accent, dark) }
        else scheme(look.accent, dark)
    }
    val hero = remember(look.accent, dark) { AppColors.hero(look.accent, dark) }
    val card = remember(dark) { AppColors.card(dark) }
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(), LocalCairnPalette provides hero, LocalCardColors provides card) {
        MaterialTheme(colorScheme = scheme, typography = cairnTypography(), content = content)
    }
}
