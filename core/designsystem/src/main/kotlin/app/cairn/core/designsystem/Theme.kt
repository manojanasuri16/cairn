package app.cairn.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF3F5F90), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71), tertiary = Color(0xFF6F5675),
    background = Color(0xFFF9F9FF), surface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    error = Color(0xFFBA1A1A),
)
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF254777), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC), tertiary = Color(0xFFDABCE2),
    background = Color(0xFF111318), surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    error = Color(0xFFFFB4AB),
)

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

/** Dynamic colour when the system provides it, otherwise a fixed palette. Light and dark follow the system. */
@Composable
fun CairnTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors: ColorScheme = runCatching {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse { if (darkTheme) FallbackDark else FallbackLight }
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(colorScheme = colors, typography = cairnTypography(), content = content)
    }
}
