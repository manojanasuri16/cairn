package app.cairn.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapTargetsTest {
    private fun c(name: String, label: String? = null, exported: Boolean = true, enabled: Boolean = true, perm: String? = null) =
        ActivityCandidate(name, label, exported, enabled, perm)

    @Test fun filteringRules() {
        val out = CorosScreens.launchable(
            listOf(
                c("a.Sleep", "Sleep"),
                c("a.Hidden", "Hidden", exported = false),
                c("a.Off", "Off", enabled = false),
                c("a.Guarded", "Guarded", perm = "a.PERM"),
                c("a.Blank", "Blank", perm = " "),
                c("a.Sleep", "Sleep again"),
                c("a.Plain", null),
                c("a.Same", "a.Same"),
            ),
        )
        assertEquals(listOf("a.Blank", "a.Plain", "a.Same", "a.Sleep"), out.map { it.className })
        assertEquals("Plain", out.first { it.className == "a.Plain" }.title)
        assertNull(out.first { it.className == "a.Same" }.label)
    }

    @Test fun searchMatchesLabelAndClass() {
        val list = CorosScreens.launchable(listOf(c("x.y.SleepDetailActivity", "Sleep"), c("x.y.RunActivity", "Run log")))
        assertEquals(2, CorosScreens.filter(list, "  ").size)
        assertEquals(listOf("x.y.SleepDetailActivity"), CorosScreens.filter(list, "sleep").map { it.className })
        assertEquals(listOf("x.y.RunActivity"), CorosScreens.filter(list, "RUN activity").map { it.className })
        assertTrue(CorosScreens.filter(list, "zzz").isEmpty())
    }

    @Test fun persistenceRoundTrip() {
        listOf(TapTarget.CorosHome, TapTarget.OpenCairn, TapTarget.CorosScreen("com.x.Y\$Inner")).forEach {
            assertEquals(it, TapTargets.decode(TapTargets.encode(it), WidgetKind.Sleep))
        }
    }

    @Test fun decodeFallsBackToDefault() {
        WidgetKind.entries.forEach { k ->
            assertEquals(TapTarget.CorosHome, TapTargets.decode(null, k))
            assertEquals(TapTarget.CorosHome, TapTargets.decode("junk", k))
            assertEquals(TapTarget.CorosHome, TapTargets.decode("screen:", k))
            assertEquals(TapTarget.CorosHome, TapTargets.decode("screen:bad name;rm", k))
        }
        assertTrue(TapTargets.KNOWN_DEFAULTS.isEmpty())
        assertEquals(WidgetKind.entries.size, WidgetKind.entries.map { TapTargets.storageKey(it) }.toSet().size)
    }

    @Test fun chains() {
        val s = TapTarget.CorosScreen("a.B")
        assertEquals(listOf(LaunchStep.Screen("a.B"), LaunchStep.CorosLaunch, LaunchStep.Explain), TapLaunchPlanner.chain(s, true))
        assertEquals(listOf(LaunchStep.Explain), TapLaunchPlanner.chain(s, false))
        assertEquals(listOf(LaunchStep.CorosLaunch, LaunchStep.Explain), TapLaunchPlanner.chain(TapTarget.CorosHome, true))
        assertEquals(listOf(LaunchStep.OpenCairn, LaunchStep.Explain), TapLaunchPlanner.chain(TapTarget.OpenCairn, true))
        assertTrue(LaunchStep.Screen("a.B").opensCoros && LaunchStep.CorosLaunch.opensCoros)
        assertFalse(LaunchStep.Explain.opensCoros || LaunchStep.OpenCairn.opensCoros)
    }

    @Test fun executeFallsThroughFailuresAndExceptions() {
        val tried = mutableListOf<LaunchStep>()
        val chain = TapLaunchPlanner.chain(TapTarget.CorosScreen("a.B"), true)
        val worked = TapLaunchPlanner.execute(chain) { step ->
            tried += step
            when (step) {
                is LaunchStep.Screen -> throw SecurityException("denied")
                LaunchStep.CorosLaunch -> throw IllegalStateException("boom")
                else -> true
            }
        }
        assertEquals(LaunchStep.Explain, worked)
        assertEquals(chain, tried)
    }

    @Test fun executeStopsAtFirstSuccessAndReportsTotalFailure() {
        val chain = TapLaunchPlanner.chain(TapTarget.CorosScreen("a.B"), true)
        assertEquals(LaunchStep.Screen("a.B"), TapLaunchPlanner.execute(chain) { true })
        assertNull(TapLaunchPlanner.execute(chain) { false })
    }
}
