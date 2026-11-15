package app.cairn.core.model

import org.junit.Test
import org.junit.Assert.assertEquals

class DisplayPrefsTest {
    @Test fun defaultsWhenMissingOrInvalid() {
        val p = DisplayPrefs.of(null, 99, null)
        assertEquals(DisplayPrefs(DistanceUnit.Km, 3, 12), p)
        assertEquals(DistanceUnit.Km, DistanceUnit.parse("bogus"))
    }

    @Test fun agingNeverBelowFresh() {
        assertEquals(6, DisplayPrefs.of("Mi", 6, 6).agingHours)
        assertEquals(DistanceUnit.Mi, DisplayPrefs.of("Mi", 1, 12).unit)
    }
}
