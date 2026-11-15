package app.cairn.sync

import kotlinx.serialization.Serializable

/** Why a sync run was requested. */
enum class SyncReason { PERIODIC, MANUAL, APP_START, SIGN_IN, PERMISSION }

enum class SyncOutcome {
    /** Everything that was due ran and succeeded. */
    SUCCESS,
    /** Something was written, something failed. */
    PARTIAL,
    /** Nothing could be fetched because the network failed; WorkManager should back off and retry. */
    NETWORK_ERROR,
    /** COROS rejected our session after refresh. No retry until the user signs in again. */
    NEEDS_SIGN_IN,
    /** Nothing was due or possible (no permission, not signed in, inside the MCP gap). */
    SKIPPED,
    /** Unexpected failure (never thrown out of the engine). */
    FAILED,
}

/** Coarse error classes. Never contains messages, tokens or health data. */
enum class ErrorType { AUTH, NETWORK, SERVER, ARGS, TOOL_MISSING, HEALTH_CONNECT, UNKNOWN }

data class SyncResult(
    val outcome: SyncOutcome,
    val mcpCalls: List<String> = emptyList(),
    val hcRead: Boolean = false,
    val errors: List<ErrorType> = emptyList(),
    /** Why MCP did not run (or ran partially); null when it ran normally. */
    val mcpNote: String? = null,
    val retry: Boolean = false,
)

@Serializable
data class RunRecord(
    val atMs: Long,
    val reason: String,
    val outcome: String,
    val mcpCalls: List<String> = emptyList(),
    val hcRead: Boolean = false,
    val errors: List<String> = emptyList(),
    val note: String? = null,
)

@Serializable
data class StopRecord(val atMs: Long, val code: Int, val name: String)

/** Everything the engine and diagnostics screen persist. No health values, only times, counters, ids and a digest. */
@Serializable
data class SyncState(
    val lastAttemptMs: Long? = null,
    val lastSuccessMs: Long? = null,
    val lastMcpRunMs: Long? = null,
    /** Last time the MCP daily+recovery replies differed from the previous run (digest comparison). */
    val lastMcpDataChangeMs: Long? = null,
    val mcpDigest: String? = null,
    val lastHcDataMs: Long? = null,
    val toolLastRunMs: Map<String, Long> = emptyMap(),
    val latestSleepDate: String? = null,
    val knownActivityIds: List<String> = emptyList(),
    val pendingLoadRefresh: Boolean = false,
    val lastErrorType: String? = null,
    val needsSignIn: Boolean = false,
    val mcpCallsDay: String? = null,
    val mcpCallsToday: Int = 0,
    val runs: List<RunRecord> = emptyList(),
    val stops: List<StopRecord> = emptyList(),
    /** tools/list result as `{name: inputSchema}` JSON text; refreshed daily. */
    val schemasJson: String? = null,
    val schemasFetchedMs: Long? = null,
)
