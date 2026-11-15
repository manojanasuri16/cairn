package app.cairn.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every receiver in the manifest has a provider XML whose layout, preview layout, preview image and description resources exist. */
class ProviderResourcesTest {
    private val main = listOf("src/main", "widget/src/main").map(::File).first { it.isDirectory }
    private fun res(type: String, name: String) = File(main, "res").listFiles { f -> f.isDirectory && f.name.startsWith(type) }!!.any { d -> d.listFiles()!!.any { it.nameWithoutExtension == name } }

    @Test fun everyReceiverHasItsProviderAndPreviewResources() {
        val manifest = File(main, "AndroidManifest.xml").readText()
        val receivers = Regex("""<receiver android:name="\.(\w+WidgetReceiver)"[^>]*>\s*<intent-filter>.*?</intent-filter>\s*<meta-data[^>]*resource="@xml/(\w+)"""", RegexOption.DOT_MATCHES_ALL).findAll(manifest).toList()
        // 7 original widgets x 3 sizes plus the four new ones x 3 sizes.
        assertEquals(33, receivers.size)
        val strings = File(main, "res/values/strings.xml").readText()
        for (r in receivers) {
            val xml = File(main, "res/xml/${r.groupValues[2]}.xml")
            assertTrue(xml.path, xml.exists())
            val text = xml.readText()
            for (ref in Regex("""@(layout|drawable|string)/(\w+)""").findAll(text)) {
                val (type, name) = ref.destructured
                if (type == "string") assertTrue("${xml.name} $name", strings.contains("name=\"$name\"")) else assertTrue("${xml.name} $name", res(type, name))
            }
            assertTrue("${xml.name} preview", text.contains("previewLayout"))
        }
        for (k in listOf("hrv", "heart", "stress", "mileage")) {
            for (r in receivers.filter { it.groupValues[2].startsWith("widget_${k}_") }) assertTrue(File(main, "res/xml/${r.groupValues[2]}.xml").readText().contains("previewImage"))
        }
        // Every receiver class is declared in the manifest once.
        assertEquals(receivers.size, receivers.map { it.groupValues[1] }.toSet().size)
    }
}
