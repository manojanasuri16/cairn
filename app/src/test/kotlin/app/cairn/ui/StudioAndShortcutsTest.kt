package app.cairn.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAndShortcutsTest {
    @Test fun studioShortcutExtraLandsOnTheStudioRoute() {
        assertEquals(Routes.WIDGETS, Routes.forScreen("Studio"))
        assertEquals(Routes.WIDGETS, Routes.forScreen("nonsense"))
        assertEquals("Add widget", Routes.title(Routes.STUDIO))
    }

    @Test fun staticShortcutsTargetTheStudioAndTheCorosTrampoline() {
        val xml = listOf("src/main/res/xml/shortcuts.xml", "app/src/main/res/xml/shortcuts.xml").map(::File).first { it.exists() }.readText()
        assertTrue(xml.contains("shortcutId=\"add_widget\"") && xml.contains("android:value=\"Studio\"") && xml.contains("app.cairn.MainActivity"))
        assertTrue(xml.contains("shortcutId=\"open_coros\"") && xml.contains(app.cairn.widget.OPEN_COROS_CLASS))
        assertEquals(2, Regex("<shortcut\\b").findAll(xml).count())
    }

    @Test fun heroRingFillsTheCardWithinSensibleBounds() {
        assertEquals(240f, HeroFit.ringSize(600f), 0.01f)      // never above the max
        assertEquals(0.7f * 300f, HeroFit.ringSize(300f), 0.01f) // about 70 percent of the content width
        assertEquals(120f, HeroFit.ringSize(100f), 0.01f)      // never below the floor
        assertTrue(HeroFit.ringSize(328f) <= 328f)
    }
}
