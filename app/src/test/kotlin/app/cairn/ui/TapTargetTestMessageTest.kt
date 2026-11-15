package app.cairn.ui

import app.cairn.widget.LaunchStep
import app.cairn.widget.TapTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTargetTestMessageTest {
    private val screen = TapTarget.CorosScreen("a.B")

    @Test fun messages() {
        assertNull(TapTargetTest.message(screen, LaunchStep.Screen("a.B")))
        assertEquals("That screen would not open, so COROS home was used instead.", TapTargetTest.message(screen, LaunchStep.CorosLaunch))
        assertEquals("COROS could not be opened, so Cairn showed help instead.", TapTargetTest.message(TapTarget.CorosHome, LaunchStep.Explain))
        assertEquals("Nothing could be opened.", TapTargetTest.message(TapTarget.OpenCairn, null))
        assertNull(TapTargetTest.message(TapTarget.OpenCairn, LaunchStep.OpenCairn))
        assertEquals("a.B".substringAfterLast('.'), screen.describe())
    }
}
