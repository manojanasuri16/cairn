package app.cairn.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cairn.widget.ResolvedConfig
import app.cairn.widget.ThumbnailMath
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** About 16 bitmaps, keyed by kind, size, resolved choices, density and the pixel width, so scrolling never re-renders. */
private val thumbCache = LruCache<String, Bitmap>(48)

/**
 * A widget at its true proportions and a realistic size ([WidgetSize.pinDp]: 2x2 124 dp square, 4x2 272 x 124, 4x3 272 x 198), drawn at
 * the exact pixels of its slot (dp times density), so it is never scaled up. It is only shrunk when the slot is narrower, and centred
 * by the caller. The slot is a neutral surface only while loading. Sample data stands in for live data ("dataVersion" is constant).
 */
@Composable
fun WidgetThumbnail(kind: WidgetKind, cfg: ResolvedConfig, size: WidgetSize, modifier: Modifier = Modifier, maxWidth: Dp = 400.dp) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val real = size.pinDp
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = ThumbnailMath.scale(real.width.value, minOf(this.maxWidth, maxWidth).value)
        val wPx = ThumbnailMath.slotPx(real.width.value, density, scale)
        val hPx = ThumbnailMath.slotPx(real.height.value, density, scale)
        val shape = RoundedCornerShape(ThumbnailMath.cornerDp(scale).dp)
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val key = "${kind.name}|${size.name}|${cfg.hashCode()}|$density|$wPx|$dark|sample"
        var bmp by remember(key) { mutableStateOf(thumbCache.get(key)) }
        LaunchedEffect(key) {
            if (bmp != null) return@LaunchedEffect
            val b = runCatching {
                val v = WidgetThumbnails.view(context, kind, cfg, real)
                withContext(Dispatchers.Default) { WidgetThumbnails.render(v, wPx) }
            }.getOrNull()
            if (b != null) { thumbCache.put(key, b); bmp = b }
        }
        // Exact slot size in dp = px / density, so the bitmap maps 1:1 to screen pixels.
        val slot = Modifier.size((wPx / density).dp, (hPx / density).dp).clip(shape)
        val b = bmp
        if (b == null) Box(slot.background(MaterialTheme.colorScheme.surfaceVariant))
        else Image(b.asImageBitmap(), "Preview of the ${kind.label} widget", slot, contentScale = ContentScale.None)
    }
}
