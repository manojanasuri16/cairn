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

    @Test fun fitMath() {
        assertEquals(1f, ThumbnailMath.aspect(WidgetSize.Small.pinDp), 0.001f)
        assertEquals(2.3f, ThumbnailMath.aspect(WidgetSize.Wide.pinDp), 0.15f)
        assertEquals(1.4f, ThumbnailMath.aspect(WidgetSize.Large.pinDp), 0.1f)
        assertEquals(0.5f, ThumbnailMath.scale(300f, 150f), 0.001f)
        assertEquals(1f, ThumbnailMath.scale(100f, 400f), 0.001f)
        assertEquals(12f, ThumbnailMath.cornerDp(0.5f), 0.001f)
        assertEquals(640, ThumbnailMath.bitmapWidthPx(1000f, 3f))
    }
}
