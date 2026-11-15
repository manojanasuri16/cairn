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
}

data class McpPlan(val refreshSchemas: Boolean, val tools: List<String>)

/** Pure decisions about which MCP tools run and how often MCP runs at all. */
object McpPlanner {
    /** Hard cap per run, counting a `tools/list` refresh. (Session initialize/notify are protocol overhead, not counted.) */
    const val MAX_CALLS_PER_RUN = 4
    const val DAILY_CAP = 120
    val MIN_GAP: Duration = Duration.ofMinutes(25)
    val MANUAL_GAP: Duration = Duration.ofMinutes(5)
    val SLOW_GAP: Duration = Duration.ofMinutes(60)
    val STALE_DATA_AFTER: Duration = Duration.ofHours(6)
    val LOAD_EVERY: Duration = Duration.ofHours(6)
    val SPORT_EVERY: Duration = Duration.ofHours(6)
    val FITNESS_EVERY: Duration = Duration.ofHours(24)
    val SLEEP_RETRY: Duration = Duration.ofMinutes(60)
    val SCHEMA_EVERY: Duration = Duration.ofHours(24)

    /** Minimum time between MCP runs for [reason] given how long MCP data has been unchanged. */
    fun minGap(reason: SyncReason, state: SyncState, now: Instant): Duration = when {
        reason == SyncReason.SIGN_IN -> Duration.ZERO
        reason == SyncReason.MANUAL -> MANUAL_GAP
        // Adaptive: if the MCP replies have not changed for 6 h the phone probably has not synced; poll hourly.
        state.lastMcpDataChangeMs?.let { Duration.between(Instant.ofEpochMilli(it), now) >= STALE_DATA_AFTER } == true -> SLOW_GAP
        else -> MIN_GAP
    }

    fun gapElapsed(reason: SyncReason, state: SyncState, now: Instant): Boolean {
        val last = state.lastMcpRunMs ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now) >= minGap(reason, state, now)
    }

    private fun due(state: SyncState, tool: String, every: Duration, now: Instant): Boolean {
        val last = state.toolLastRunMs[tool] ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now) >= every
    }

    fun plan(state: SyncState, now: Instant, zone: ZoneId, newExercise: Boolean): McpPlan {
        val today = LocalDate.ofInstant(now, zone)
        val refresh = state.schemasJson == null || state.schemasFetchedMs?.let { Duration.between(Instant.ofEpochMilli(it), now) >= SCHEMA_EVERY } != false
        val want = mutableListOf(McpTools.DAILY, McpTools.RECOVERY)
        val sleepMissing = state.latestSleepDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it < today } ?: true
        if (sleepMissing && due(state, McpTools.SLEEP, SLEEP_RETRY, now)) want += McpTools.SLEEP
        if (newExercise || state.pendingLoadRefresh || due(state, McpTools.LOAD, LOAD_EVERY, now)) want += McpTools.LOAD
        if (newExercise || due(state, McpTools.SPORT, SPORT_EVERY, now)) want += McpTools.SPORT
        if (due(state, McpTools.FITNESS, FITNESS_EVERY, now)) want += McpTools.FITNESS
        return McpPlan(refresh, want.take(MAX_CALLS_PER_RUN - if (refresh) 1 else 0))
    }
}
