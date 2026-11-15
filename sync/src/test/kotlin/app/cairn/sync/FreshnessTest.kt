package app.cairn.sync

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreshnessTest {
    private val z = ZoneOffset.UTC
    private val noon = Instant.parse("2030-01-04T12:00:00Z")
    private fun ms(hAgo: Long, from: Instant = noon) = from.minusSeconds(hAgo * 3600).toEpochMilli()
    private fun d(enabled: Boolean = true, th: Int = 12, perm: Boolean = true, newest: Long? = ms(13), last: String? = null, now: Instant = noon) =
        StaleNudgePolicy.decide(enabled, th, perm, newest, last, now, z)

    @Test fun showsWhenOverThreshold() = assertEquals(StaleNudgePolicy.Decision.Show(13), d())
    @Test fun thresholdBoundary() {
        assertEquals(StaleNudgePolicy.Decision.Show(12), d(newest = ms(12)))
        assertEquals(StaleNudgePolicy.Decision.Cancel, d(newest = ms(11)))
        assertEquals(StaleNudgePolicy.Decision.Cancel, d(th = 24, newest = ms(13)))
    }
    @Test fun wakingHoursOnly() {
        assertEquals(StaleNudgePolicy.Decision.Nothing, d(now = Instant.parse("2030-01-04T07:59:00Z"), newest = Instant.parse("2030-01-03T10:00:00Z").toEpochMilli()))
        assertEquals(StaleNudgePolicy.Decision.Nothing, d(now = Instant.parse("2030-01-04T22:00:00Z")))
        assertTrue(d(now = Instant.parse("2030-01-04T08:00:00Z"), newest = Instant.parse("2030-01-03T10:00:00Z").toEpochMilli()) is StaleNudgePolicy.Decision.Show)
    }
    @Test fun wakingHoursUseLocalZone() {
        val tokyo = ZoneOffset.ofHours(9) // 12:00Z = 21:00 local: allowed; 14:00Z = 23:00 local: not
        assertTrue(StaleNudgePolicy.decide(true, 12, true, ms(20), null, noon, tokyo) is StaleNudgePolicy.Decision.Show)
        assertEquals(StaleNudgePolicy.Decision.Nothing, StaleNudgePolicy.decide(true, 12, true, ms(20), null, Instant.parse("2030-01-04T14:00:00Z"), tokyo))
    }
    @Test fun oncePerDay() {
        assertEquals(StaleNudgePolicy.Decision.Nothing, d(last = "2030-01-04"))
        assertTrue(d(last = "2030-01-03") is StaleNudgePolicy.Decision.Show)
    }
    @Test fun freshCancels() = assertEquals(StaleNudgePolicy.Decision.Cancel, d(newest = ms(1)))
    @Test fun disabledCancelsAndNeverShows() = assertEquals(StaleNudgePolicy.Decision.Cancel, d(enabled = false))
    @Test fun permissionDeniedNeverShows() = assertEquals(StaleNudgePolicy.Decision.Nothing, d(perm = false))
    @Test fun noDataDoesNothing() = assertEquals(StaleNudgePolicy.Decision.Nothing, d(newest = null))

    private class Notifier(var perm: Boolean = true) : StaleNotifier {
        var shown = 0; var cancelled = 0
        override fun hasPermission() = perm
        override fun show(ageHours: Long) { shown++ }
        override fun cancel() { cancelled++ }
    }
    private class Settings(var on: Boolean = true) : NudgeSettings {
        var day: String? = null
        override val enabled get() = kotlinx.coroutines.flow.flowOf(on)
        override val thresholdHours get() = kotlinx.coroutines.flow.flowOf(12)
        override suspend fun isEnabled() = on
        override suspend fun threshold() = 12
        override suspend fun setEnabled(on: Boolean) { this.on = on }
        override suspend fun setThreshold(hours: Int) = Unit
        override suspend fun lastNotifiedDay() = day
        override suspend fun setLastNotifiedDay(day: String?) { this.day = day }
    }

    @Test fun evaluatorUsesNewestAcrossSourcesAndShowsOnce() = kotlinx.coroutines.test.runTest {
        val n = Notifier(); val s = Settings()
        val store = InMemoryStateStore(SyncState(lastHcDataMs = ms(30), lastMcpDataChangeMs = ms(14)))
        val e = StaleNudgeEvaluator(s, store, n, { noon }, z)
        e.evaluate(); e.evaluate()
        assertEquals(1, n.shown)
        store.update { it.copy(lastHcDataMs = ms(1)) }
        e.evaluate()
        assertEquals(1, n.cancelled)
    }

    private fun st(lastMcp: Long?) = SyncState(lastMcpRunMs = lastMcp)

    @Test fun manualGate() {
        val s = ManualSync
        assertTrue(s.gate(st(null), noon, true, true, z) is ManualSync.Gate.Allowed)
        assertTrue((s.gate(st(null), noon, false, true, z) as ManualSync.Gate.Skipped).message.contains("Not signed in"))
        assertTrue((s.gate(st(null), noon, true, false, z) as ManualSync.Gate.Skipped).message.contains("offline"))
        val inside = s.gate(st(noon.minusSeconds(120).toEpochMilli()), noon, true, true, z) as ManualSync.Gate.Skipped
        assertTrue(inside.message, inside.message.contains("3 min (at 12:03)"))
        assertEquals(noon.plusSeconds(180).toEpochMilli(), inside.retryAtMs)
        assertTrue(s.gate(st(noon.minusSeconds(300).toEpochMilli()), noon, true, true, z) is ManualSync.Gate.Allowed)
    }

    @Test fun manualResult() {
        fun run(o: String, at: Long = 1000) = SyncState(runs = listOf(RunRecord(at, "MANUAL", o)))
        assertEquals(null, ManualSync.result(SyncState(), 0))
        assertEquals(null, ManualSync.result(run("SUCCESS", 500), 1000))
        assertEquals("Synced.", ManualSync.result(run("SUCCESS"), 1000))
        assertTrue(ManualSync.result(run("NEEDS_SIGN_IN"), 0)!!.contains("sign in"))
    }

    @Test fun corosRefreshPlan() {
        assertEquals(listOf(90L, 390L), CorosRefreshPlanner.delaysSeconds())
        assertEquals(CorosRefreshPlanner.Launch.OpenCoros, CorosRefreshPlanner.decide(true))
        assertEquals(CorosRefreshPlanner.Launch.ExplainInCairn, CorosRefreshPlanner.decide(false))
        assertEquals(2, CorosRefreshPlanner.delaysSeconds().indices.map { CorosRefreshPlanner.workName(it) }.toSet().size)
    }

    @Test fun corosRefreshBypassesGapButNotDailyCap() {
        val recent = st(noon.minusSeconds(60).toEpochMilli())
        assertTrue(McpPlanner.gapElapsed(SyncReason.COROS_REFRESH, recent, noon))
        assertTrue(!McpPlanner.gapElapsed(SyncReason.PERIODIC, recent, noon))
        assertTrue(!McpPlanner.gapElapsed(SyncReason.MANUAL, recent, noon))
    }

    @Test fun refreshRequestHasDelayAndReason() {
        val r = SyncScheduler.corosRefreshRequest(90)
        assertEquals(90_000L, r.workSpec.initialDelay)
        assertEquals(SyncReason.COROS_REFRESH.name, r.workSpec.input.getString(SyncScheduler.KEY_REASON))
    }
}
