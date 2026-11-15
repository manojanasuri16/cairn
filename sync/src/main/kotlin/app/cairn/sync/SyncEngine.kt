package app.cairn.sync

import app.cairn.core.model.Activity
import app.cairn.data.Clock
import app.cairn.source.corosmcp.CorosAuthException
import app.cairn.source.corosmcp.CorosException
import app.cairn.source.corosmcp.CorosNetworkException
import app.cairn.source.corosmcp.parse.DailyHealthParser
import app.cairn.source.corosmcp.parse.FitnessParser
import app.cairn.source.corosmcp.parse.RecoveryParser
import app.cairn.source.corosmcp.parse.SleepOverviewParser
import app.cairn.source.corosmcp.parse.SportRecordsParser
import app.cairn.source.corosmcp.parse.ToolReply
import app.cairn.source.corosmcp.parse.TrainingLoadParser
import app.cairn.source.healthconnect.HealthConnectMapper
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One [syncOnce] = Health Connect re-read of the recent window + a budgeted set of MCP calls, all written to Room.
 * Never throws (except coroutine cancellation). Contains no Android types so it runs in plain JVM tests.
 *
 * Health Connect: instead of per-type changes tokens we always re-read the last [HC_WINDOW_DAYS] local days and
 * upsert. Daily rows are keyed (date, source) and activities by `hc-<record id>`, so re-reading is idempotent
 * (de-duplicated by record id) and an expired token cannot exist. The window starts at a local midnight so day
 * totals are complete. Cost is small (2 days of data).
 */
class SyncEngine(
    private val hc: HealthSource,
    private val coros: CorosGateway,
    private val writer: SyncWriter,
    private val store: SyncStateStore,
    private val widgets: WidgetUpdater = NoOpWidgetUpdater,
    private val clock: Clock = Clock { Instant.now() },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nudge: StaleNudgeEvaluator? = null,
) {
    private val mapper = HealthConnectMapper(zone)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    companion object {
        const val HC_WINDOW_DAYS = 2L
        const val MAX_RUNS = 20
        const val MAX_STOPS = 10
        const val MAX_KNOWN_IDS = 60
    }

    suspend fun syncOnce(reason: SyncReason): SyncResult = mutex.withLock {
        val run = Run(clock.now(), reason)
        try {
            execute(run)
        } catch (e: CancellationException) {
            run.errors += ErrorType.UNKNOWN
            run.note = "cancelled"
            throw e
        } catch (e: Throwable) {
            run.errors += ErrorType.UNKNOWN
            run.fatal = true
        } finally {
            withContext(NonCancellable) { finish(run) }
        }
        run.result()
    }

    private class Run(val now: Instant, val reason: SyncReason) {
        val errors = mutableListOf<ErrorType>()
        val calls = mutableListOf<String>()
        var hcRead = false
        var wrote = false
        var note: String? = null
        var fatal = false
        var needsSignIn = false
        var networkFailed = false
        var mcpStarted = false
        var mcpOk = false
        var hcDataMs: Long? = null
        var latestSleep: LocalDate? = null
        var newActivityIds = emptyList<String>()
        var sawNewMcpActivity = false
        val toolRuns = mutableMapOf<String, Long>()
        var schemas: Pair<String, Long>? = null
        var dailyText: String? = null
        var recoveryText: String? = null
        var clearLoadPending = false
        var warnings = 0

        fun outcomeNow(): SyncOutcome = when {
            fatal -> SyncOutcome.FAILED
            needsSignIn -> SyncOutcome.NEEDS_SIGN_IN
            errors.isEmpty() -> if (hcRead || calls.isNotEmpty()) SyncOutcome.SUCCESS else SyncOutcome.SKIPPED
            wrote -> SyncOutcome.PARTIAL
            networkFailed -> SyncOutcome.NETWORK_ERROR
            else -> SyncOutcome.FAILED
        }

        fun result() = SyncResult(
            outcome = outcomeNow(), mcpCalls = calls.toList(), hcRead = hcRead, errors = errors.distinct(),
            mcpNote = note, retry = networkFailed && !mcpOk,
        )
    }

    private suspend fun execute(run: Run) {
        val s0 = store.update { it.copy(lastAttemptMs = run.now.toEpochMilli()) }
        val today = LocalDate.ofInstant(run.now, zone)
        var known = s0.knownActivityIds.toSet()

        // 1. Health Connect.
        var newExercise = false
        try {
            if (hc.canRead()) {
                val from = today.minusDays(HC_WINDOW_DAYS - 1).atStartOfDay(zone).toInstant()
                val inputs = hc.read(from, run.now)
                run.hcRead = true
                val daily = mapper.dailySummaries(inputs.steps, inputs.heartRate, inputs.restingHr, inputs.distance, inputs.calories)
                val sleep = mapper.sleepNights(inputs.sleep)
                val acts = mapper.activities(inputs.exercise, inputs.distance, inputs.calories)
                writer.storeDaily(daily); writer.storeSleep(sleep); writer.storeActivities(acts)
                run.wrote = true
                newExercise = acts.any { it.id !in known }
                run.newActivityIds = acts.map { it.id }
                run.latestSleep = sleep.maxOfOrNull { it.date }
                run.hcDataMs = (inputs.steps.map { it.lastModified } + inputs.heartRate.map { it.lastModified } +
                    inputs.restingHr.map { it.lastModified } + inputs.sleep.map { it.lastModified } +
                    inputs.exercise.map { it.lastModified }).maxOrNull()?.toEpochMilli()
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            run.errors += ErrorType.HEALTH_CONNECT
        }

        // 2. COROS MCP.
        if (!coros.isSignedIn()) { run.note = "not signed in"; return }
        val s1 = store.get()
        if (s1.needsSignIn && run.reason != SyncReason.SIGN_IN) { run.note = "needs sign-in"; return }
        val day = today.toString()
        val usedToday = if (s1.mcpCallsDay == day) s1.mcpCallsToday else 0
        if (usedToday >= McpPlanner.DAILY_CAP) { run.note = "daily budget reached"; return }
        if (!McpPlanner.gapElapsed(run.reason, s1, run.now)) { run.note = "inside minimum gap"; return }
        run.mcpStarted = true

        val plan = McpPlanner.plan(s1, run.now, zone, newExercise, run.reason)
        var schemas: Map<String, JsonObject> = parseSchemas(s1.schemasJson)
        if (plan.refreshSchemas) {
            try {
                schemas = coros.listToolSchemas()
                run.calls += "tools/list"
                run.schemas = JsonObject(schemas).toString() to run.now.toEpochMilli()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                run.calls += "tools/list"
                if (classify(run, e)) return
            }
        }
        for (tool in plan.tools) {
            if (schemas.isNotEmpty() && tool !in schemas) { run.errors += ErrorType.TOOL_MISSING; run.toolRuns[tool] = run.now.toEpochMilli(); continue }
            val built = ToolArgs.build(schemas[tool], requestFor(tool), today)
            if (built.unresolved.isNotEmpty()) { run.errors += ErrorType.ARGS; run.toolRuns[tool] = run.now.toEpochMilli(); continue }
            try {
                run.calls += tool
                val reply = coros.call(tool, built.args)
                run.toolRuns[tool] = run.now.toEpochMilli()
                if (reply.isError) { run.errors += ErrorType.SERVER; continue }
                handle(run, tool, reply, known)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (classify(run, e)) return
            }
        }
    }

    private fun requestFor(tool: String) = when (tool) {
        McpTools.DAILY -> ArgRequest(days = 7)
        McpTools.SLEEP -> ArgRequest(days = 3)
        McpTools.SPORT -> ArgRequest(limit = 5)
        else -> ArgRequest()
    }

    /** Parses and writes one reply; a parse that finds nothing (warnings) is not an error. */
    private suspend fun handle(run: Run, tool: String, reply: ToolReply, known: Set<String>) {
        val now = run.now
        when (tool) {
            McpTools.DAILY -> {
                val r = DailyHealthParser.parse(reply, now)
                run.warnings += r.warnings.size
                writer.storeDaily(r.value.days); writer.storeSleep(r.value.sleep)
                run.dailyText = reply.text
                r.value.sleep.maxOfOrNull { it.date }?.let { run.latestSleep = maxOf(run.latestSleep ?: it, it) }
            }
            McpTools.RECOVERY -> {
                val r = RecoveryParser.parse(reply, now)
                run.warnings += r.warnings.size
                r.value?.let { writer.storeRecovery(it) }
                run.recoveryText = reply.text
            }
            McpTools.SLEEP -> {
                val r = SleepOverviewParser.parse(reply, now, zone)
                run.warnings += r.warnings.size
                writer.storeSleep(r.value)
                r.value.maxOfOrNull { it.date }?.let { run.latestSleep = maxOf(run.latestSleep ?: it, it) }
            }
            McpTools.LOAD -> {
                val r = TrainingLoadParser.parse(reply, now)
                run.warnings += r.warnings.size
                writer.storeLoad(r.value)
                run.clearLoadPending = true
            }
            McpTools.FITNESS -> {
                val r = FitnessParser.parse(reply, now)
                run.warnings += r.warnings.size
                r.value?.let { writer.storeFitness(it) }
            }
            McpTools.SPORT -> {
                val r = SportRecordsParser.parse(reply, now)
                run.warnings += r.warnings.size
                writer.storeActivities(r.value)
                val ids = r.value.map(Activity::id)
                if (known.isNotEmpty() && ids.any { it !in known }) run.sawNewMcpActivity = true
                run.newActivityIds = run.newActivityIds + ids
            }
        }
        run.wrote = true
        run.mcpOk = true
    }

    /** Records the error; returns true when remaining MCP calls must stop. */
    private fun classify(run: Run, e: Exception): Boolean = when (e) {
        is CorosAuthException -> { run.errors += ErrorType.AUTH; run.needsSignIn = true; true }
        is CorosNetworkException -> { run.errors += ErrorType.NETWORK; run.networkFailed = true; true }
        is CorosException -> { run.errors += ErrorType.SERVER; false }
        else -> { run.errors += ErrorType.UNKNOWN; false }
    }

    private suspend fun finish(run: Run) {
        val outcome = run.outcomeNow()
        val nowMs = run.now.toEpochMilli()
        val day = LocalDate.ofInstant(run.now, zone).toString()
        // A first-call network failure did not really reach MCP, so it must not start the 25 min gap.
        val mcpRan = run.mcpStarted && (run.mcpOk || !run.networkFailed)
        val digest = if (run.dailyText != null) sha256((run.dailyText ?: "") + "|" + (run.recoveryText ?: "")) else null
        store.update { s ->
            val sameDay = s.mcpCallsDay == day
            val changed = digest != null && digest != s.mcpDigest
            val ids = (run.newActivityIds + s.knownActivityIds).distinct().take(SyncEngine.MAX_KNOWN_IDS)
            val sleepDate = listOfNotNull(s.latestSleepDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, run.latestSleep).maxOrNull()
            s.copy(
                lastSuccessMs = if (outcome == SyncOutcome.SUCCESS || outcome == SyncOutcome.PARTIAL) nowMs else s.lastSuccessMs,
                lastMcpRunMs = if (mcpRan) nowMs else s.lastMcpRunMs,
                lastMcpDataChangeMs = if (changed || (digest != null && s.lastMcpDataChangeMs == null)) nowMs else s.lastMcpDataChangeMs,
                mcpDigest = digest ?: s.mcpDigest,
                lastHcDataMs = run.hcDataMs?.let { maxOf(it, s.lastHcDataMs ?: 0L) } ?: s.lastHcDataMs,
                toolLastRunMs = s.toolLastRunMs + run.toolRuns,
                latestSleepDate = sleepDate?.toString(),
                knownActivityIds = ids,
                pendingLoadRefresh = if (run.clearLoadPending) run.sawNewMcpActivity else s.pendingLoadRefresh || run.sawNewMcpActivity,
                lastErrorType = run.errors.firstOrNull()?.name,
                needsSignIn = if (run.needsSignIn) true else if (run.reason == SyncReason.SIGN_IN && run.mcpStarted) false else s.needsSignIn,
                mcpCallsDay = day,
                mcpCallsToday = (if (sameDay) s.mcpCallsToday else 0) + run.calls.size,
                userSyncRequestedMs = null,
                schemasJson = run.schemas?.first ?: s.schemasJson,
                schemasFetchedMs = run.schemas?.second ?: s.schemasFetchedMs,
                runs = (listOf(
                    RunRecord(
                        nowMs, run.reason.name, outcome.name, run.calls.toList(), run.hcRead, run.errors.map { it.name },
                        listOfNotNull(run.note, run.warnings.takeIf { it > 0 }?.let { "$it parse warnings" }).joinToString("; ").ifEmpty { null },
                    ),
                ) + s.runs).take(SyncEngine.MAX_RUNS),
            )
        }
        runCatching { widgets.updateAll() }
        runCatching { nudge?.evaluate() }
    }

    private fun parseSchemas(text: String?): Map<String, JsonObject> =
        if (text == null) emptyMap()
        else runCatching { json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonObject } }.getOrDefault(emptyMap())

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
