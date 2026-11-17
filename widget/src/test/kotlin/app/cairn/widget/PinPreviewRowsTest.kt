package app.cairn.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinPreviewRowsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun texts(rv: android.widget.RemoteViews): List<String> {
        val root = FrameLayout(context)
        root.addView(rv.apply(context, root))
        val out = mutableListOf<String>()
        fun walk(v: View) { if (v is TextView && v.text.isNotEmpty()) out += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(root)
        return out
    }

    @Test fun lastActivityHeroPinPreviewHasEveryRow() {
        val cfg = WidgetConfig(style = WidgetStyle.ActivityHero).resolve(WidgetKind.LastActivity, AppAppearance())
        val t = texts(runBlocking { WidgetCatalog.previewViews(context, WidgetKind.LastActivity, cfg, WidgetSize.Wide.pinDp) })
        println("PINTEXTS $t")
        for (s in listOf("10.20 km", "52 min", "5'07\" /km", "152 bpm")) assertTrue("$s in $t", t.any { it.contains(s) })
    }

    @Test fun pinDialogPreviewIsASinglePicture() {
        val cfg = WidgetConfig(style = WidgetStyle.ActivityHero).resolve(WidgetKind.LastActivity, AppAppearance())
        val rv = runBlocking { WidgetCatalog.pinPreviewViews(context, WidgetKind.LastActivity, cfg, WidgetSize.Wide) }
        val root = rv.apply(context, android.widget.FrameLayout(context))
        assertTrue("expected an ImageView, got ${root.javaClass.simpleName}", root is android.widget.ImageView)
        assertTrue(PinPreview.extras(rv) != null)
    }
}
