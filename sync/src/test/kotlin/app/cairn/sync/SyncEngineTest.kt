package app.cairn.sync

import app.cairn.source.corosmcp.CorosAuthException
import app.cairn.source.corosmcp.CorosException
import app.cairn.source.corosmcp.CorosNetworkException
import app.cairn.source.healthconnect.ExerciseSessionInput
import app.cairn.source.healthconnect.HcInputs
import app.cairn.source.healthconnect.StepsInput
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private val clock = FakeClock()
    private val hc = FakeHc()
    private val coros = FakeCoros()
    private val writer = FakeWriter()
    private val store = InMemoryStateStore()
    private val widgets = CountingWidgets()
    private val engine = SyncEngine(hc, coros, writer, store, widgets, clock, ZoneOffset.UTC)

    private fun schema(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val hcSteps = HcInputs(steps = listOf(StepsInput(Instant.parse("2030-01-04T08:00:00Z"), Instant.parse("2030-01-04T09:00:00Z"), 1000, Instant.parse("2030-01-04T09:30:00Z"))))
    private fun exercise(id: String) = ExerciseSessionInput(id, Instant.parse("2030-01-04T07:00:00Z"), Instant.parse("2030-01-04T08:00:00Z"), "Run", Instant.parse("2030-01-04T08:05:00Z"))

    /** State where every tool ran recently and a sleep night for today exists, so only daily+recovery are due. */
    private fun settled(nowMs: Long = clock.now.toEpochMilli()) = SyncState(
        lastMcpRunMs = nowMs - Duration.ofHours(1).toMillis(),
        lastMcpDataChangeMs = nowMs - Duration.ofMinutes(30).toMillis(),
        toolLastRunMs = McpTools.run { listOf(DAILY, RECOVERY, SLEEP, LOAD, FITNESS, SPORT) }.associateWith { nowMs - Duration.ofMinutes(10).toMillis() },
        latestSleepDate = "2030-01-04",
        schemasJson = "{}", schemasFetchedMs = nowMs - Duration.ofMinutes(10).toMillis(),
    )

    @Test fun firstRunRefreshesSchemasThenRunsPriorityToolsWithinBudget() = runTest {
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(1, coros.listCalls)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY, McpTools.SLEEP), coros.toolNames)
        assertEquals(4, r.mcpCalls.size)
        assertEquals(SyncOutcome.SUCCESS, r.outcome)
        assertEquals(1, writer.recovery.size)
        assertTrue(writer.daily.isNotEmpty())
        assertEquals(4, store.get().mcpCallsToday)
    }

    @Test fun neverMoreThanFourCallsAndRemainingToolsRunNextRun() = runTest {
        engine.syncOnce(SyncReason.PERIODIC)
        clock.advance(Duration.ofMinutes(26)); coros.reset()
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertTrue(r.mcpCalls.size <= 4)
        // Schemas are fresh; daily+recovery always; load, sport, fitness were never run so they follow (4 max).
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY, McpTools.LOAD, McpTools.SPORT), coros.toolNames)
        assertEquals(0, coros.listCalls)
    }

    @Test fun insideMinimumGapMcpIsSkippedButHealthConnectStillRuns() = runTest {
        hc.inputs = hcSteps
        engine.syncOnce(SyncReason.PERIODIC)
        clock.advance(Duration.ofMinutes(10)); coros.reset(); hc.reads = 0
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertTrue(coros.calls.isEmpty())
        assertEquals("inside minimum gap", r.mcpNote)
        assertEquals(1, hc.reads)
        assertTrue(r.hcRead)
    }

    @Test fun manualRunUsesShortGapSignInHasNone() = runTest {
        engine.syncOnce(SyncReason.PERIODIC)
        clock.advance(Duration.ofMinutes(6)); coros.reset()
        engine.syncOnce(SyncReason.MANUAL)
        assertTrue(coros.calls.isNotEmpty())
    }

    @Test fun adaptivePollingMovesToHourlyWhenMcpDataUnchangedForSixHours() = runTest {
        val now = clock.now.toEpochMilli()
        store.update { settled(now).copy(lastMcpRunMs = now - Duration.ofMinutes(30).toMillis(), lastMcpDataChangeMs = now - Duration.ofHours(7).toMillis()) }
        engine.syncOnce(SyncReason.PERIODIC)
        assertTrue("30 min is too soon while data is stale", coros.calls.isEmpty())
        store.update { it.copy(lastMcpRunMs = now - Duration.ofMinutes(61).toMillis()) }
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY), coros.toolNames)
    }

    @Test fun unchangedRepliesDoNotAdvanceDataChangeTimeChangedRepliesDo() = runTest {
        engine.syncOnce(SyncReason.PERIODIC)
        val first = store.get().lastMcpDataChangeMs!!
        clock.advance(Duration.ofMinutes(30))
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(first, store.get().lastMcpDataChangeMs)
        clock.advance(Duration.ofMinutes(30))
        coros.replies[McpTools.RECOVERY] = RECOVERY_TEXT.replace("64%", "70%")
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(clock.now.toEpochMilli(), store.get().lastMcpDataChangeMs)
    }

    @Test fun sleepOverviewOnlyWhenLatestNightIsOlderThanToday() = runTest {
        store.update { settled().copy(lastMcpRunMs = clock.now.toEpochMilli() - Duration.ofHours(1).toMillis()) }
        engine.syncOnce(SyncReason.PERIODIC)
        assertFalse("tonight's sleep is already stored", McpTools.SLEEP in coros.toolNames)
        coros.reset(); clock.advance(Duration.ofMinutes(30))
        // Yesterday's night is the newest, and sleep was last tried over an hour ago.
        store.update { it.copy(latestSleepDate = "2030-01-03", toolLastRunMs = it.toolLastRunMs + (McpTools.SLEEP to clock.now.toEpochMilli() - Duration.ofHours(2).toMillis())) }
        engine.syncOnce(SyncReason.PERIODIC)
        assertTrue(McpTools.SLEEP in coros.toolNames)
        // Having just tried, it is not retried at the next run (budget protection).
        coros.reset(); clock.advance(Duration.ofMinutes(30))
        store.update { it.copy(latestSleepDate = "2030-01-03") }
        engine.syncOnce(SyncReason.PERIODIC)
        assertFalse(McpTools.SLEEP in coros.toolNames)
    }

    @Test fun newHealthConnectExerciseTriggersLoadAndSportRecords() = runTest {
        store.update { settled() }
        hc.inputs = HcInputs(exercise = listOf(exercise("abc")))
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY, McpTools.LOAD, McpTools.SPORT), coros.toolNames)
        assertTrue(writer.activities.any { it.id == "hc-abc" })
        // The same session again is not new.
        clock.advance(Duration.ofMinutes(30)); coros.reset()
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY), coros.toolNames)
    }

    @Test fun staleLoadSportAndFitnessRunOnTheirOwnSchedules() = runTest {
        val now = clock.now.toEpochMilli()
        store.update {
            settled(now).copy(toolLastRunMs = settled(now).toolLastRunMs + mapOf(
                McpTools.LOAD to now - Duration.ofHours(7).toMillis(),
                McpTools.SPORT to now - Duration.ofHours(5).toMillis(),
                McpTools.FITNESS to now - Duration.ofHours(25).toMillis(),
            ))
        }
        engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY, McpTools.LOAD, McpTools.FITNESS), coros.toolNames)
    }

    @Test fun authFailureMarksNeedsSignInStopsAndDoesNotRetryStorm() = runTest {
        coros.failures[McpTools.DAILY] = CorosAuthException("Unauthorized after refresh")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.NEEDS_SIGN_IN, r.outcome)
        assertFalse(r.retry)
        assertEquals(listOf(McpTools.DAILY), coros.toolNames)
        assertTrue(store.get().needsSignIn)
        assertEquals(ErrorType.AUTH.name, store.get().lastErrorType)
        // Later runs make no MCP call at all.
        clock.advance(Duration.ofHours(2)); coros.reset()
        val r2 = engine.syncOnce(SyncReason.PERIODIC)
        assertTrue(coros.calls.isEmpty() && coros.listCalls == 0)
        assertEquals("needs sign-in", r2.mcpNote)
        // Signing in again clears it.
        coros.failures.clear()
        engine.syncOnce(SyncReason.SIGN_IN)
        assertFalse(store.get().needsSignIn)
        assertTrue(coros.calls.isNotEmpty())
    }

    @Test fun authFailureOnToolsListIsHandledToo() = runTest {
        coros.listFailure = CorosAuthException("Session expired")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.NEEDS_SIGN_IN, r.outcome)
        assertTrue(coros.calls.isEmpty())
    }

    @Test fun networkFailureWithNothingElseIsNetworkError() = runTest {
        hc.can = false
        coros.failures[McpTools.DAILY] = CorosNetworkException("Network error: X")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.NETWORK_ERROR, r.outcome)
        assertTrue(r.retry)
    }

    @Test fun networkFailureAsksForBackoffAndDoesNotStartTheGap() = runTest {
        coros.failures[McpTools.DAILY] = CorosNetworkException("Network error: UnknownHostException")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.PARTIAL, r.outcome) // Health Connect part succeeded, MCP did not
        assertTrue(r.retry)
        assertEquals(listOf(ErrorType.NETWORK), r.errors)
        assertNull(store.get().lastMcpRunMs)
        assertFalse(store.get().needsSignIn)
        coros.failures.clear(); coros.reset()
        clock.advance(Duration.ofSeconds(60))
        assertEquals(SyncOutcome.SUCCESS, engine.syncOnce(SyncReason.PERIODIC).outcome)
    }

    @Test fun partialFailureStillWritesWhatSucceeded() = runTest {
        coros.failures[McpTools.RECOVERY] = CorosException("MCP HTTP 500")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.PARTIAL, r.outcome)
        assertTrue(writer.daily.isNotEmpty())
        assertTrue(writer.recovery.isEmpty())
        assertEquals(listOf(McpTools.DAILY, McpTools.RECOVERY, McpTools.SLEEP), coros.toolNames) // carried on
        assertFalse(r.retry)
        assertEquals(ErrorType.SERVER.name, store.get().lastErrorType)
        assertTrue(store.get().lastSuccessMs != null)
    }

    @Test fun healthConnectFailureDoesNotBlockMcpAndNeverThrows() = runTest {
        hc.failure = SecurityException("permission revoked")
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertTrue(ErrorType.HEALTH_CONNECT in r.errors)
        assertEquals(SyncOutcome.PARTIAL, r.outcome)
        assertTrue(coros.toolNames.isNotEmpty())
    }

    @Test fun writerFailureNeverEscapes() = runTest {
        val bad = object : SyncWriter by writer { override suspend fun storeRecovery(recovery: app.cairn.core.model.Recovery) = error("db") }
        val e = SyncEngine(hc, coros, bad, store, widgets, clock, ZoneOffset.UTC)
        val r = e.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.PARTIAL, r.outcome) // the recovery write failed, daily succeeded
        assertTrue(ErrorType.UNKNOWN in r.errors)
        assertEquals(1, store.get().runs.size)
    }

    @Test fun skippedWhenNothingToDo() = runTest {
        hc.can = false; coros.signedIn = false
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(SyncOutcome.SKIPPED, r.outcome)
        assertEquals("not signed in", r.mcpNote)
    }

    @Test fun widgetsUpdatedAfterEveryRunAndRunsAreRecorded() = runTest {
        engine.syncOnce(SyncReason.PERIODIC)
        engine.syncOnce(SyncReason.PERIODIC) // skipped by gap
        assertEquals(2, widgets.count)
        val s = store.get()
        assertEquals(2, s.runs.size)
        assertEquals("PERIODIC", s.runs[0].reason)
        assertNull(s.runs.flatMap { it.errors }.firstOrNull())
    }

    @Test fun dailyBudgetCapStopsMcp() = runTest {
        store.update { it.copy(mcpCallsDay = "2030-01-04", mcpCallsToday = McpPlanner.DAILY_CAP) }
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals("daily budget reached", r.mcpNote)
        assertTrue(coros.calls.isEmpty())
    }

    @Test fun toolArgumentsComeFromTheSchemaAndUnknownRequiredArgsSkipTheCall() = runTest {
        coros.schemas = mapOf(
            McpTools.DAILY to schema("""{"type":"object","properties":{"startDate":{"type":"string","description":"YYYYMMDD"},"endDate":{"type":"string","description":"YYYYMMDD"}},"required":["startDate","endDate"]}"""),
            McpTools.RECOVERY to schema("""{"type":"object","properties":{"mystery":{"type":"string"}},"required":["mystery"]}"""),
            McpTools.SLEEP to schema("""{"type":"object","properties":{}}"""),
        )
        val r = engine.syncOnce(SyncReason.PERIODIC)
        val daily = coros.calls.first { it.first == McpTools.DAILY }.second
        assertEquals("20300104", daily["endDate"].toString().trim('"'))
        assertEquals("20291229", daily["startDate"].toString().trim('"')) // 7 days ending 2030-01-04
        assertFalse(McpTools.RECOVERY in coros.toolNames)
        assertTrue(ErrorType.ARGS in r.errors)
        assertEquals(SyncOutcome.PARTIAL, r.outcome)
    }

    @Test fun toolMissingFromListingIsSkipped() = runTest {
        coros.schemas = mapOf(McpTools.DAILY to schema("""{"type":"object","properties":{}}"""))
        val r = engine.syncOnce(SyncReason.PERIODIC)
        assertEquals(listOf(McpTools.DAILY), coros.toolNames)
        assertTrue(ErrorType.TOOL_MISSING in r.errors)
    }
}
