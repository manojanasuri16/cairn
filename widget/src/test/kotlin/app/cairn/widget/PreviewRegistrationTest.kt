package app.cairn.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every receiver is registered for a generated preview and has a static preview resource; previews are not composed at the minimum size. */
class PreviewRegistrationTest {
    private val main = listOf("src/main", "widget/src/main").map(::File).first { it.isDirectory }

    @Test fun allReceiversCoverEveryManifestReceiver() {
        val manifest = File(main, "AndroidManifest.xml").readText()
        val declared = Regex("""<receiver android:name="\.(\w+WidgetReceiver)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        val registered = WidgetVariants.allReceivers().map { it.simpleName }.toSet()
        assertEquals(33, registered.size)
        assertEquals(declared, registered)
    }

    @Test fun everyProviderHasAStaticPreview() {
        for (f in File(main, "res/xml").listFiles { x -> x.name.startsWith("widget_") && x.name.endsWith("_info.xml") }!!) {
            val t = f.readText()
            assertTrue(f.name, t.contains("previewLayout") || t.contains("previewImage"))
        }
    }

    @Test fun pinPreviewLimit() {
        assertTrue(PinPreview.fits(1_000)); assertTrue(!PinPreview.fits(PinPreview.MAX_BYTES + 1)); assertTrue(!PinPreview.fits(0))
    }
}

class PinPreviewSizeTest {
    @Test fun cellEstimateMatchesVariantAndPickMatchesPlacedWidget() {
        for (k in WidgetKind.entries) for (s in WidgetSize.entries) {
            val d = s.cellDp
            assertEquals("$k $s w", s.cw, WidgetSize.cellsOf(d.width.value))
            assertEquals("$k $s h", s.ch, WidgetSize.cellsOf(d.height.value))
            // The same breakpoint the placed widget gets at that cell size.
            val sizes = WidgetSizes.byKind.getValue(k)
            val pick = WidgetSizes.pick(sizes, d.width.value, d.height.value)
            assertTrue("$k $s", pick in sizes)
        }
    }
}

class ProviderMinSizeTest {
    @Test fun minSizeMapsBackToTargetCells() {
        val dir = listOf("src/main/res/xml", "widget/src/main/res/xml").map(::File).first { it.isDirectory }
        for (f in dir.listFiles { x -> x.name.startsWith("widget_") && x.name.endsWith("_info.xml") }!!) {
            val t = f.readText()
            fun num(a: String) = Regex("""android:$a="(\d+)""").find(t)!!.groupValues[1].toFloat()
            assertEquals("${f.name} w", num("targetCellWidth").toInt(), WidgetSize.cellsOf(num("minWidth")))
            assertEquals("${f.name} h", num("targetCellHeight").toInt(), WidgetSize.cellsOf(num("minHeight")))
        }
    }
}
