package app.cairn.ui

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.ThemeId
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.Part
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetStyle
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LooksTest {
    @Test fun eightCuratedLooksWithUniqueIdsAndSafeOpacity() {
        assertEquals(7, WidgetLooks.curated.size)
        assertEquals(7, WidgetLooks.curated.map { it.id }.toSet().size)
        WidgetLooks.curated.forEach { assertTrue(it.opacity == 0 || it.opacity == 100); assertEquals(it.bg == Bg.Transparent, it.opacity == 0) }
    }

    @Test fun withLookChangesOnlyLookFields() {
        val base = WidgetConfig(style = WidgetStyle.TodayStack, hidden = setOf(Part.Rings), compactFooter = true)
        val out = base.withLook(WidgetLooks.byId("deep-ocean")!!)
        assertEquals(ThemeId.Ocean, out.theme); assertEquals(Bg.Gradient, out.bg); assertEquals(AccentOption.Sky, out.accent)
        assertEquals(base.style, out.style); assertEquals(base.hidden, out.hidden); assertTrue(out.compactFooter)
    }

    @Test fun ofRecognisesAppliedLookAndCustomOtherwise() {
        WidgetLooks.curated.forEach { assertEquals(it, WidgetLooks.of(WidgetConfig().withLook(it))) }
        assertNull(WidgetLooks.of(WidgetConfig().withLook(WidgetLooks.curated[0]).copy(opacity = 80)))
        assertNull(WidgetLooks.of(WidgetConfig()))
    }

    @Test fun resetReturnsToInheritAndKeepsContent() {
        val cfg = WidgetConfig(hidden = setOf(Part.Rings)).withLook(WidgetLooks.curated[3]).copy(style = WidgetStyle.TodayStack)
        val r = cfg.resetLook()
        assertNull(r.theme); assertNull(r.bg); assertNull(r.opacity); assertNull(r.style)
        assertEquals(Corners.System, r.corners); assertEquals(setOf(Part.Rings), r.hidden)
    }

    @Test fun surpriseNeverRepeatsCurrentOrPicksGlass() {
        val cur = WidgetLooks.byId("dusk")
        repeat(300) { val s = WidgetLooks.surprise(cur, Random(it)); assertNotEquals("dusk", s.id); assertNotEquals("glass", s.id) }
    }

    @Test fun undoIsEnabledOnlyWhenSomethingChanged() {
        val initial = WidgetConfig()
        assertFalse(EditorUndo.canUndo(initial, WidgetConfig()))
        assertTrue(EditorUndo.canUndo(initial, initial.withLook(WidgetLooks.curated[1])))
    }

    @Test fun applyToAllThenUndoRestoresExactPreviousConfigs() = runBlocking {
        val store = mutableMapOf(
            1 to WidgetConfig(style = WidgetStyle.TodayStack, theme = ThemeId.Dawn, compactFooter = true),
            2 to WidgetConfig(hidden = setOf(Part.Rings)),
        )
        val before = store.mapValues { it.value.encode() }
        val refreshes = intArrayOf(0)
        val a = LookApplier(
            ids = { listOf(1 to WidgetKind.Today, 2 to WidgetKind.Recovery) },
            load = { id, _ -> store.getValue(id) }, save = { id, c -> store[id] = c },
            refresh = { refreshes[0]++ }, decode = { raw, k -> WidgetConfig.decode(raw, k) },
        )
        assertEquals(2, a.applyAll(WidgetLooks.byId("forest")!!))
        assertEquals(ThemeId.Forest, store.getValue(1).theme)
        assertEquals(WidgetStyle.TodayStack, store.getValue(1).style)
        assertTrue(store.getValue(1).compactFooter)
        assertEquals(setOf(Part.Rings), store.getValue(2).hidden)
        assertTrue(a.canUndo)
        assertEquals(2, a.undo())
        assertEquals(before, store.mapValues { it.value.encode() })
        assertFalse(a.canUndo); assertEquals(0, a.undo()); assertEquals(2, refreshes[0])
    }
}
