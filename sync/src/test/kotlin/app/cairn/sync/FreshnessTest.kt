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
        // No 5 minute gap any more: a sync 2 minutes after the last run is allowed.
        assertTrue(s.gate(SyncState(lastMcpRunMs = noon.minusSeconds(120).toEpochMilli(), lastAttemptMs = noon.minusSeconds(120).toEpochMilli()), noon, true, true, z) is ManualSync.Gate.Allowed)
        // Debounce: a second tap within 10 s does not stack, and says so.
        val deb = s.gate(SyncState(userSyncRequestedMs = noon.minusSeconds(3).toEpochMilli()), noon, true, true, z) as ManualSync.Gate.Skipped
        assertEquals("Already syncing.", deb.message)
        assertTrue(s.gate(SyncState(userSyncRequestedMs = noon.minusSeconds(11).toEpochMilli()), noon, true, true, z) is ManualSync.Gate.Allowed)
        assertTrue(s.gate(SyncState(lastAttemptMs = noon.minusSeconds(4).toEpochMilli()), noon, true, true, z) is ManualSync.Gate.Skipped)
        // Daily cap is generous but reported, never silent.
        val capped = SyncState(mcpCallsDay = "2030-01-04", mcpCallsToday = McpPlanner.DAILY_CAP)
        val cap = s.gate(capped, java.time.Instant.parse("2030-01-04T12:00:00Z"), true, true, java.time.ZoneOffset.UTC) as ManualSync.Gate.Skipped
        assertTrue(cap.message, cap.message.contains("daily limit"))
        assertTrue(s.gate(capped, java.time.Instant.parse("2030-01-05T12:00:00Z"), true, true, java.time.ZoneOffset.UTC) is ManualSync.Gate.Allowed)
        assertEquals(600, McpPlanner.DAILY_CAP)
    }

    @Test fun indicatorStates() {
        val min = java.time.Duration.ofMinutes(1)
        fun d(tier: app.cairn.core.model.Freshness, age: java.time.Duration, st: SyncState? = null) = SyncIndicators.derive(tier, age, st, noon)
        assertEquals(IndicatorKind.Fresh, d(app.cairn.core.model.Freshness.Fresh, min).kind)
        assertEquals(null, d(app.cairn.core.model.Freshness.Fresh, min).shortAge)
        assertEquals("5h", d(app.cairn.core.model.Freshness.Aging, java.time.Duration.ofHours(5)).shortAge)
        assertEquals("3d", d(app.cairn.core.model.Freshness.Stale, java.time.Duration.ofDays(3)).shortAge)
        assertTrue(d(app.cairn.core.model.Freshness.Aging, java.time.Duration.ofHours(5)).description.contains("Updated 5h ago"))
        val syncing = d(app.cairn.core.model.Freshness.Stale, java.time.Duration.ofDays(3), SyncState(userSyncRequestedMs = noon.minusSeconds(5).toEpochMilli()))
        assertEquals(IndicatorKind.Syncing, syncing.kind); assertEquals(null, syncing.shortAge)
        // A marker older than the window is not "syncing" any more.
        assertEquals(IndicatorKind.Fresh, d(app.cairn.core.model.Freshness.Fresh, min, SyncState(userSyncRequestedMs = noon.minusSeconds(600).toEpochMilli())).kind)
        val failed = SyncState(runs = listOf(RunRecord(noon.minusSeconds(30).toEpochMilli(), "USER", "NETWORK_ERROR")))
        val err = d(app.cairn.core.model.Freshness.Fresh, min, failed)
        assertEquals(IndicatorKind.Error, err.kind); assertTrue(err.description.contains("tap to retry"))
        // A failed background run is not shown as a user error.
        assertEquals(IndicatorKind.Fresh, d(app.cairn.core.model.Freshness.Fresh, min, SyncState(runs = listOf(RunRecord(noon.minusSeconds(30).toEpochMilli(), "PERIODIC", "FAILED")))).kind)
        assertTrue(SyncIndicators.justSucceeded(SyncState(runs = listOf(RunRecord(noon.minusSeconds(2).toEpochMilli(), "USER", "SUCCESS"))), noon))
        assertTrue(!SyncIndicators.justSucceeded(SyncState(runs = listOf(RunRecord(noon.minusSeconds(20).toEpochMilli(), "USER", "SUCCESS"))), noon))
        assertEquals("now", SyncIndicators.shortAge(java.time.Duration.ofSeconds(20)))
        assertEquals("14h", SyncIndicators.shortAge(java.time.Duration.ofHours(14)))
    }

    @Test fun statusLineStates() {
        fun ind(t: app.cairn.core.model.Freshness, age: java.time.Duration, st: SyncState? = null) = SyncIndicators.derive(t, age, st, noon)
        assertEquals(null, StatusLine.text(ind(app.cairn.core.model.Freshness.Fresh, java.time.Duration.ofMinutes(2)), false))
        assertEquals("Up to date", StatusLine.text(ind(app.cairn.core.model.Freshness.Fresh, java.time.Duration.ofMinutes(2)), true))
        assertEquals("14h", StatusLine.text(ind(app.cairn.core.model.Freshness.Stale, java.time.Duration.ofHours(14)), false))
        assertEquals("Syncing…", StatusLine.text(ind(app.cairn.core.model.Freshness.Stale, java.time.Duration.ofHours(14), SyncState(userSyncRequestedMs = noon.toEpochMilli())), true))
        val bad = SyncState(runs = listOf(RunRecord(noon.minusSeconds(10).toEpochMilli(), "MANUAL", "NETWORK_ERROR")))
        assertTrue(StatusLine.errorMessage(bad, noon, 0)!!.contains("Network"))
        assertEquals(null, StatusLine.errorMessage(bad, noon, noon.toEpochMilli()))
        assertEquals(null, StatusLine.errorMessage(SyncState(runs = listOf(RunRecord(noon.minusSeconds(10).toEpochMilli(), "PERIODIC", "FAILED"))), noon, 0))
        assertEquals(null, StatusLine.errorMessage(bad, noon.plusSeconds(3600), 0))
        val cap = SyncState(runs = listOf(RunRecord(noon.minusSeconds(10).toEpochMilli(), "USER", "SKIPPED", note = "daily budget reached")))
        assertTrue(StatusLine.errorMessage(cap, noon, 0)!!.contains("limit"))
        assertTrue(!StatusLine.errorMessage(bad, noon, 0)!!.contains("again in"))
    }

    @Test fun lightSyncSelectionAndSchedule() {
        val s = SyncState(schemasJson = "{}", schemasFetchedMs = noon.toEpochMilli())
        val light = McpPlanner.plan(s, noon, z, newExercise = true, reason = SyncReason.FAST_PERIODIC)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY), light.tools)
        assertTrue(!light.refreshSchemas)
        assertTrue(McpPlanner.plan(s, noon, z, true, SyncReason.PERIODIC).tools.size > 2)
        assertEquals(SyncSchedule.FastChain(5), SyncSchedule.decide(5))
        assertEquals(SyncSchedule.Periodic(15), SyncSchedule.decide(15))
        assertEquals(SyncSchedule.Periodic(30), SyncSchedule.decide(7))
        assertEquals(listOf(5, 15, 30, 60), SyncSettings.CHOICES)
        assertEquals(java.time.Duration.ofMinutes(4), McpPlanner.minGap(SyncReason.FAST_PERIODIC, s, noon))
        assertEquals(java.time.Duration.ZERO, McpPlanner.minGap(SyncReason.USER, s, noon))
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
        assertTrue(McpPlanner.gapElapsed(SyncReason.MANUAL, recent, noon))
        assertTrue(McpPlanner.gapElapsed(SyncReason.USER, recent, noon))
    }

    @Test fun refreshRequestHasDelayAndReason() {
        val r = SyncScheduler.corosRefreshRequest(90)
        assertEquals(90_000L, r.workSpec.initialDelay)
        assertEquals(SyncReason.COROS_REFRESH.name, r.workSpec.input.getString(SyncScheduler.KEY_REASON))
    }
}
