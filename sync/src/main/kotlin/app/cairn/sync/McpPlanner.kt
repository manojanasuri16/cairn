package app.cairn.sync

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object McpTools {
    const val DAILY = "queryDailyHealthData"
    const val RECOVERY = "queryRecoveryStatus"
    const val SLEEP = "querySleepOverview"
    const val LOAD = "queryTrainingLoadAssessment"
    const val FITNESS = "queryFitnessAssessmentOverview"
    const val SPORT = "querySportRecords"
    /** Pseudo key for the previous-month window of querySportRecords (same tool, its own schedule). */
    const val SPORT_PREV = "querySportRecords:prev"
    const val DETAIL = "getActivityDetail"
    const val RESTING_HR = "queryRestingHeartRate"
    const val AVG_HR = "queryAvgHeartRate"
    const val STRESS = "queryStressLevel"
    const val HRV = "querySleepHrv"
}

/** Gate keys that include the window, so a narrower earlier call is never treated as fresh. */
fun McpTools.gateKey(tool: String, today: LocalDate): String = when (tool) {
    McpTools.SPORT -> "${McpTools.SPORT}@${today.withDayOfMonth(1)}..$today"
    McpTools.SPORT_PREV -> today.withDayOfMonth(1).minusDays(1).let { end -> "${McpTools.SPORT}@${end.withDayOfMonth(1)}..$end" }
    else -> tool
}

data class McpPlan(val refreshSchemas: Boolean, val tools: List<String>)

/** Pure decisions about which MCP tools run and how often MCP runs at all. */
object McpPlanner {
    /** Hard cap per run, counting a `tools/list` refresh. (Session initialize/notify are protocol overhead, not counted.) */
    const val MAX_CALLS_PER_RUN = 4
    /** Generous: manual use is never blocked in practice. Reaching it is reported, never silent. */
    const val DAILY_CAP = 600
    /** A person asked for the sync: the cheap list tools skip their time gates and the run may use more calls. */
    const val MAX_CALLS_USER_RUN = 8
    val MIN_GAP: Duration = Duration.ofMinutes(25)
    /** Only stops double taps stacking; a person can sync again almost at once. */
    val USER_DEBOUNCE: Duration = Duration.ofSeconds(10)
    /** Minimum between 5 minute chain runs (a little under 5 min so timing jitter never skips one). */
    val FAST_GAP: Duration = Duration.ofMinutes(4)
    val SLOW_GAP: Duration = Duration.ofMinutes(60)
    val STALE_DATA_AFTER: Duration = Duration.ofHours(6)
    val LOAD_EVERY: Duration = Duration.ofHours(6)
    val SPORT_EVERY: Duration = Duration.ofHours(6)
    val FITNESS_EVERY: Duration = Duration.ofHours(24)
    val SLEEP_RETRY: Duration = Duration.ofMinutes(60)
    val SCHEMA_EVERY: Duration = Duration.ofHours(24)
    /** Slowly changing 7-day summaries (heart rate, stress, HRV): at most every few hours. */
    val VITALS_EVERY: Duration = Duration.ofHours(3)
    /** A later watch sync can replace an early sleep reading, so today's night is re-read now and then. */
    val SLEEP_REFRESH: Duration = Duration.ofHours(3)
    /** Activity details are fetched only for new activities, this many per run at most. */
    const val MAX_DETAILS_PER_RUN = 2

    /** Minimum time between MCP runs for [reason] given how long MCP data has been unchanged. */
    fun minGap(reason: SyncReason, state: SyncState, now: Instant): Duration = when {
        reason == SyncReason.SIGN_IN || reason == SyncReason.COROS_REFRESH || reason.userInitiated -> Duration.ZERO
        reason == SyncReason.FAST_PERIODIC -> FAST_GAP
        // Adaptive: if the MCP replies have not changed for 6 h the phone probably has not synced; poll hourly.
        state.lastMcpDataChangeMs?.let { Duration.between(Instant.ofEpochMilli(it), now) >= STALE_DATA_AFTER } == true -> SLOW_GAP
        else -> MIN_GAP
    }

    fun gapElapsed(reason: SyncReason, state: SyncState, now: Instant): Boolean {
        val last = state.lastMcpRunMs ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now) >= minGap(reason, state, now)
    }

    private fun due(state: SyncState, tool: String, every: Duration, now: Instant, key: String = tool): Boolean {
        val last = state.toolLastRunMs[key] ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now) >= every
    }

    /** Light run (the 5 minute chain): recovery and daily only, a schema refresh only when none is cached. */
    fun plan(state: SyncState, now: Instant, zone: ZoneId, newExercise: Boolean, reason: SyncReason, pendingDetails: Int = 0): McpPlan =
        if (reason == SyncReason.FAST_PERIODIC) McpPlan(state.schemasJson == null, listOf(McpTools.DAILY, McpTools.RECOVERY))
        else plan(state, now, zone, newExercise, pendingDetails, force = reason.userInitiated || reason == SyncReason.SIGN_IN || reason == SyncReason.COROS_REFRESH)

    fun plan(state: SyncState, now: Instant, zone: ZoneId, newExercise: Boolean, pendingDetails: Int = 0, force: Boolean = false): McpPlan {
        val today = LocalDate.ofInstant(now, zone)
        val refresh = state.schemasJson == null || state.schemasFetchedMs?.let { Duration.between(Instant.ofEpochMilli(it), now) >= SCHEMA_EVERY } != false
        val want = mutableListOf(McpTools.DAILY, McpTools.RECOVERY)
        val sleepMissing = state.latestSleepDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it < today } ?: true
        if ((sleepMissing && due(state, McpTools.SLEEP, SLEEP_RETRY, now)) || (!sleepMissing && due(state, McpTools.SLEEP, SLEEP_REFRESH, now))) want += McpTools.SLEEP
        if (newExercise || state.pendingLoadRefresh || due(state, McpTools.LOAD, LOAD_EVERY, now)) want += McpTools.LOAD
        if (force || newExercise || due(state, McpTools.SPORT, SPORT_EVERY, now, McpTools.gateKey(McpTools.SPORT, today))) want += McpTools.SPORT
        repeat(pendingDetails.coerceIn(0, MAX_DETAILS_PER_RUN)) { want += McpTools.DETAIL }
        if (force || due(state, McpTools.SPORT_PREV, SPORT_EVERY, now, McpTools.gateKey(McpTools.SPORT_PREV, today))) want += McpTools.SPORT_PREV
        if (due(state, McpTools.FITNESS, FITNESS_EVERY, now)) want += McpTools.FITNESS
        for (t in listOf(McpTools.RESTING_HR, McpTools.AVG_HR, McpTools.STRESS, McpTools.HRV)) if (force || due(state, t, VITALS_EVERY, now)) want += t
        val cap = if (force) MAX_CALLS_USER_RUN else MAX_CALLS_PER_RUN
        return McpPlan(refresh, want.take(cap - if (refresh) 1 else 0))
    }
}
