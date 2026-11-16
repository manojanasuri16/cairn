package app.cairn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLayoutTest {
    @Test fun previewHeightIsThirtyFivePercentOnTallWindows() {
        assertEquals(280f, EditorLayout.previewMaxHeightDp(800f), 0.01f)
    }
    @Test fun previewHeightHasMinimumsAndShortWindowRule() {
        assertEquals(196f, EditorLayout.previewMaxHeightDp(560f), 0.01f)
    }
    @Test fun shortWindowUsesThirtyPercentWithFloor() {
        assertEquals(150f, EditorLayout.previewMaxHeightDp(500f), 0.01f)
        assertEquals(104f, EditorLayout.previewMaxHeightDp(300f), 0.01f)
    }
    @Test fun scaleNeverUpscales() { assertEquals(1f, EditorLayout.scale(100f, 50f, 400f, 400f), 0f) }
    @Test fun scaleKeepsAspectByUsingTheTighterAxis() {
        assertEquals(0.5f, EditorLayout.scale(300f, 140f, 150f, 400f), 0.001f)
        assertEquals(0.5f, EditorLayout.scale(170f, 170f, 400f, 85f), 0.001f)
    }
    @Test fun scaleIsSafeForDegenerateInput() {
        assertEquals(1f, EditorLayout.scale(0f, 0f, 10f, 10f), 0f)
        assertTrue(EditorLayout.scale(300f, 140f, 0f, 0f) > 0f)
    }
    @Test fun sideBySideFromSixHundredDp() {
        assertFalse(EditorLayout.sideBySide(411f)); assertTrue(EditorLayout.sideBySide(600f)); assertTrue(EditorLayout.sideBySide(851f))
    }
}
