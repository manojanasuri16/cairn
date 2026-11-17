package app.cairn.widget

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ThumbnailTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun texts(v: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (v is TextView && v.text.isNotEmpty()) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun view(kind: WidgetKind, size: WidgetSize) =
        runBlocking { WidgetThumbnails.view(context, kind, WidgetConfig().resolve(kind, AppAppearance()), size.pinDp) }

    @Test fun todayThumbnailIsLaidOutAtItsRealSizeWithContent() {
        val v = view(WidgetKind.Today, WidgetSize.Small)
        val d = context.resources.displayMetrics.density
        assertEquals((WidgetSize.Small.pinDp.width.value * d).toInt(), v.width)
        assertTrue(texts(v).isNotEmpty())
    }

    @Test fun lastActivityThumbnailHasItsNumbers() {
        val t = texts(view(WidgetKind.LastActivity, WidgetSize.Wide))
        assertTrue("$t", t.any { it.contains("10.20 km") })
    }

    @Test fun bitmapWidthEqualsSlotWidthInPixels() {
        val d = context.resources.displayMetrics.density
        val real = WidgetSize.Wide.pinDp
        val v = view(WidgetKind.Today, WidgetSize.Wide)
        val px = ThumbnailMath.slotPx(real.width.value, d, 1f)
        val bmp = WidgetThumbnails.render(v, px)
        assertEquals(px, bmp.width) // drawn 1:1, no scaling
        assertEquals(ThumbnailMath.slotPx(real.height.value, d, 1f), bmp.height)
    }

    @Test fun fitMath() {
        assertEquals(1f, ThumbnailMath.aspect(WidgetSize.Small.pinDp), 0.001f)
        assertEquals(2.2f, ThumbnailMath.aspect(WidgetSize.Wide.pinDp), 0.1f)
        assertEquals(1.4f, ThumbnailMath.aspect(WidgetSize.Large.pinDp), 0.1f)
        assertEquals(0.5f, ThumbnailMath.scale(300f, 150f), 0.001f)
        assertEquals(1f, ThumbnailMath.scale(100f, 400f), 0.001f) // never upscaled
        assertEquals(12f, ThumbnailMath.cornerDp(0.5f), 0.001f)
        assertEquals(1000, ThumbnailMath.slotPx(250f, 4f, 1f))
        assertEquals(372, ThumbnailMath.slotPx(124f, 3f, 1f))
        assertEquals(186, ThumbnailMath.slotPx(124f, 3f, 0.5f))
        assertEquals(40f, ThumbnailMath.centreOffset(400f, 320f), 0.001f)
        assertEquals(0f, ThumbnailMath.centreOffset(100f, 320f), 0.001f)
        // aspect preserved when scaled
        val s = ThumbnailMath.scale(272f, 200f)
        assertEquals(272f / 124f, ThumbnailMath.slotPx(272f, 3f, s).toFloat() / ThumbnailMath.slotPx(124f, 3f, s), 0.03f)
    }

    @Test fun roundedOutlineViewsAreRoundInTheBitmap() {
        val d = context.resources.displayMetrics.density
        val px = (40 * d).toInt()
        val root = android.widget.FrameLayout(context)
        val dot = View(context).apply {
            setBackgroundColor(android.graphics.Color.RED)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setRoundRect(0, 0, view.width, view.height, view.width / 2f) }
            }
        }
        root.addView(dot, android.widget.FrameLayout.LayoutParams(px, px))
        root.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, px, px)
        val bmp = WidgetThumbnails.render(root, px)
        assertEquals(android.graphics.Color.RED, bmp.getPixel(px / 2, px / 2)) // centre is the dot
        assertEquals(0, android.graphics.Color.alpha(bmp.getPixel(0, 0))) // the corner is not
        assertEquals(0, android.graphics.Color.alpha(bmp.getPixel(px - 1, px - 1)))
    }
}
