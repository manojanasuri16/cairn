package app.cairn.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** About 16 small bitmaps; keyed by kind, size and the resolved choices, so scrolling the list never re-renders. */
private val thumbCache = LruCache<String, Bitmap>(16)

/**
 * A widget drawn at its real size and scaled into a slot that follows its aspect ratio (2x2 square, 4x2 wide, 4x3 taller), clipped to the
 * widget's corner radius. The slot is a neutral surface only while loading; the content is a bitmap, never a live view.
 */
@Composable
fun WidgetThumbnail(kind: WidgetKind, cfg: ResolvedConfig, size: WidgetSize, modifier: Modifier = Modifier, maxWidth: Dp = 320.dp) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val real = size.pinDp
    BoxWithConstraints(modifier) {
        val slotW = minOf(this.maxWidth, maxWidth)
        val scale = ThumbnailMath.scale(real.width.value, slotW.value)
        val shape = RoundedCornerShape(ThumbnailMath.cornerDp(scale).dp)
        val key = "${kind.name}|${size.name}|${cfg.hashCode()}|${ThumbnailMath.bitmapWidthPx(slotW.value, density)}"
        var bmp by remember(key) { mutableStateOf(thumbCache.get(key)) }
        LaunchedEffect(key) {
            if (bmp != null) return@LaunchedEffect
            val b = runCatching {
                val v = WidgetThumbnails.view(context, kind, cfg, real)
                withContext(Dispatchers.Default) { WidgetThumbnails.render(v, ThumbnailMath.bitmapWidthPx(slotW.value, density)) }
            }.getOrNull()
            if (b != null) { thumbCache.put(key, b); bmp = b }
        }
        val slot = Modifier.fillMaxWidth().aspectRatio(ThumbnailMath.aspect(real)).clip(shape)
        val b = bmp
        if (b == null) androidx.compose.foundation.layout.Box(slot.background(MaterialTheme.colorScheme.surfaceVariant))
        else Image(b.asImageBitmap(), "Preview of the ${kind.label} widget", slot, contentScale = ContentScale.FillBounds)
    }
}
