package app.cairn.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitMathTest {
    @Test fun columnsStepDownFromFourToTwoToOne() {
        assertEquals(4, FitMath.columns(320f, listOf(60f, 70f), 4f, 4))
        assertEquals(2, FitMath.columns(200f, listOf(70f, 80f), 4f, 4))
        assertEquals(1, FitMath.columns(120f, listOf(70f, 80f), 4f, 4))
        assertEquals(1, FitMath.columns(50f, listOf(70f), 4f, 4))
    }

    @Test fun rowsAreBalancedWithoutOrphans() {
        assertEquals(listOf(4), FitMath.rows(4, 4))
        assertEquals(listOf(2, 2), FitMath.rows(4, 2))
        assertEquals(listOf(2, 1), FitMath.rows(3, 2))
        assertEquals(listOf(3, 2), FitMath.rows(5, 4))
        assertEquals(listOf(3, 3), FitMath.rows(6, 4))
        assertEquals(listOf(1), FitMath.rows(1, 4))
        for (n in 1..12) for (c in 1..4) assertEquals(n, FitMath.rows(n, c).sum())
    }

    @Test fun balancedWidthsFillTheRowEdgeToEdgeAndNeverShrinkATile() {
        val needs = listOf(40f, 70f, 55f)
        val w = FitMath.balancedWidths(needs, 300f, 4f)
        assertEquals(300f, w.sum() + 8f, 0.01f)
        needs.zip(w).forEach { (n, x) -> assertTrue(x >= n) }
        assertEquals(listOf(300f), FitMath.balancedWidths(listOf(50f), 300f, 4f))
    }

    @Test fun valueSizeIsTheLargestThatFitsDownToTheFloor() {
        val width = { s: Float -> s * 5f }
        assertEquals(20f, FitMath.valueSize(width, 100f, 40f, 12f), 0.01f)
        assertEquals(12f, FitMath.valueSize(width, 30f, 40f, 12f), 0.01f)
        assertEquals(40f, FitMath.valueSize(width, 500f, 40f, 12f), 0.01f)
    }
}
