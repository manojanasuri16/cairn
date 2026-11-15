package app.cairn.widget

import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders

/** Fixed palette for devices without Material You dynamic colour. */
private val FallbackColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF1F6B5B), onPrimary = Color.White,
        surface = Color(0xFFF6FAF8), onSurface = Color(0xFF171D1B), onSurfaceVariant = Color(0xFF4A5551),
        surfaceVariant = Color(0xFFDCE5E1), error = Color(0xFFB3261E), background = Color(0xFFF6FAF8), onBackground = Color(0xFF171D1B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF7DD6BE), onPrimary = Color(0xFF00382D),
        surface = Color(0xFF101413), onSurface = Color(0xFFE0E4E1), onSurfaceVariant = Color(0xFFB5C0BB),
        surfaceVariant = Color(0xFF3A4440), error = Color(0xFFF2B8B5), background = Color(0xFF101413), onBackground = Color(0xFFE0E4E1),
    ),
)

@Composable
fun CairnGlanceTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else FallbackColors, content = content)
}
