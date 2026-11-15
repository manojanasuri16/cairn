package app.cairn.sync

import app.cairn.data.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** Delayed refreshes after the user opens COROS (it syncs the watch only while it runs). */
object CorosRefreshPlanner {
    const val FIRST_DELAY_SECONDS = 90L
    const val SECOND_DELAY_SECONDS = FIRST_DELAY_SECONDS + 5 * 60
    fun delaysSeconds(): List<Long> = listOf(FIRST_DELAY_SECONDS, SECOND_DELAY_SECONDS)
    fun workName(index: Int) = "cairn-coros-refresh-${index + 1}"

    sealed interface Launch {
        /** COROS is installed: open it and schedule the delayed refreshes. */
        data object OpenCoros : Launch
        /** Not installed: open Cairn and explain. No refreshes are scheduled. */
        data object ExplainInCairn : Launch
    }

    fun decide(corosInstalled: Boolean): Launch = if (corosInstalled) Launch.OpenCoros else Launch.ExplainInCairn
}

/** Pure text/decisions for the manual "Sync now" button. */
object ManualSync {
    sealed interface Gate {
        data object Allowed : Gate
        data class Skipped(val message: String, val retryAtMs: Long? = null) : Gate
    }

    /** Decides before enqueueing, so a skipped tap is never silent. Only a tiny debounce and the daily cap can stop a user sync. */
    fun gate(state: SyncState, now: Instant, signedIn: Boolean, online: Boolean, zone: ZoneId = ZoneId.systemDefault()): Gate {
        if (!signedIn) return Gate.Skipped("Not signed in to COROS. Sign in under COROS account, then try again.")
        if (!online) return Gate.Skipped("You are offline. Connect to the internet and try again.")
        val recent = listOfNotNull(state.userSyncRequestedMs, state.lastAttemptMs).maxOrNull()
        if (recent != null && Duration.between(Instant.ofEpochMilli(recent), now).let { !it.isNegative && it < McpPlanner.USER_DEBOUNCE }) {
            return Gate.Skipped("Already syncing.", recent + McpPlanner.USER_DEBOUNCE.toMillis())
        }
        val day = LocalDate.ofInstant(now, zone).toString()
        if (state.mcpCallsDay == day && state.mcpCallsToday >= McpPlanner.DAILY_CAP) {
            return Gate.Skipped("Cairn reached its daily limit of ${McpPlanner.DAILY_CAP} COROS requests. It resets at midnight.")
        }
        return Gate.Allowed
    }

    /** Result line for the newest run (a run recorded at or after [startedMs]); null if none yet. */
    fun result(state: SyncState, startedMs: Long): String? {
        val r = state.runs.firstOrNull() ?: return null
        if (r.atMs < startedMs) return null
        return when (r.outcome) {
            SyncOutcome.SUCCESS.name -> "Synced."
            SyncOutcome.PARTIAL.name -> "Synced with some errors: ${r.errors.joinToString().ifEmpty { "see runs below" }}."
            SyncOutcome.NETWORK_ERROR.name -> "Network problem. Cairn will retry automatically."
            SyncOutcome.NEEDS_SIGN_IN.name -> "COROS needs you to sign in again."
            SyncOutcome.SKIPPED.name -> if (r.note == "daily budget reached") "Daily COROS request limit reached. It resets at midnight." else "Nothing to do: ${r.note ?: "no source available"}."
            else -> "Sync failed${r.errors.firstOrNull()?.let { " ($it)" } ?: ""}."
        }
    }
}

/** User choices for the opt-in "COROS hasn't synced" reminder. */
interface NudgeSettings {
    val enabled: Flow<Boolean>
    val thresholdHours: Flow<Int>
    suspend fun isEnabled(): Boolean
    suspend fun threshold(): Int
    suspend fun setEnabled(on: Boolean)
    suspend fun setThreshold(hours: Int)
    suspend fun lastNotifiedDay(): String?
    suspend fun setLastNotifiedDay(day: String?)

    companion object {
        val CHOICES = listOf(6, 12, 24)
        const val DEFAULT = 12
        fun normalize(h: Int?): Int = if (h in CHOICES) h!! else DEFAULT
    }
}

/** Pure decision: show, cancel, or leave the notification alone. */
object StaleNudgePolicy {
    const val WAKE_START_HOUR = 8
    const val WAKE_END_HOUR = 22 // exclusive

    sealed interface Decision {
        data class Show(val ageHours: Long) : Decision
        data object Cancel : Decision
        data object Nothing : Decision
    }

    fun decide(
        enabled: Boolean,
        thresholdHours: Int,
        permissionGranted: Boolean,
        newestDataMs: Long?,
        lastNotifiedDay: String?,
        now: Instant,
        zone: ZoneId,
    ): Decision {
        if (!enabled) return Decision.Cancel
        if (newestDataMs == null) return Decision.Nothing
        val age = Duration.between(Instant.ofEpochMilli(newestDataMs), now)
        // Fresh data cancels any shown reminder; the once-a-day marker is kept so flapping cannot cause a second one.
        if (age < Duration.ofHours(thresholdHours.toLong())) return Decision.Cancel
        if (!permissionGranted) return Decision.Nothing
        val local = now.atZone(zone)
        if (local.hour < WAKE_START_HOUR || local.hour >= WAKE_END_HOUR) return Decision.Nothing
        if (lastNotifiedDay == LocalDate.ofInstant(now, zone).toString()) return Decision.Nothing
        return Decision.Show(age.toHours())
    }
}

interface StaleNotifier {
    fun hasPermission(): Boolean
    fun show(ageHours: Long)
    fun cancel()
}

/** Runs [StaleNudgePolicy] against the persisted state; called at the end of each sync run. */
class StaleNudgeEvaluator(
    private val settings: NudgeSettings,
    private val state: SyncStateStore,
    private val notifier: StaleNotifier,
    private val clock: Clock = Clock { Instant.now() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun evaluate() {
        val s = state.get()
        val newest = listOfNotNull(s.lastHcDataMs, s.lastMcpDataChangeMs).maxOrNull()
        val now = clock.now()
        when (val d = StaleNudgePolicy.decide(settings.isEnabled(), settings.threshold(), notifier.hasPermission(), newest, settings.lastNotifiedDay(), now, zone)) {
            is StaleNudgePolicy.Decision.Show -> {
                notifier.show(d.ageHours)
                settings.setLastNotifiedDay(LocalDate.ofInstant(now, zone).toString())
            }
            StaleNudgePolicy.Decision.Cancel -> notifier.cancel()
            StaleNudgePolicy.Decision.Nothing -> Unit
        }
    }
}

/** Plain-text status for the COROS background check. */
object BatteryCheck {
    fun label(ignoringOptimisations: Boolean?): String = when (ignoringOptimisations) {
        true -> "Unrestricted (not battery-optimised)"
        false -> "Battery-optimised. Android may delay its background sync."
        null -> "Unknown (Android does not let Cairn read this)"
    }
}

/** What the quiet freshness indicator (dot + sync icon) shows. */
enum class IndicatorKind { Fresh, Aging, Stale, Syncing, Error }

data class Indicator(
    val kind: IndicatorKind,
    /** "2h", "14h", "3d" next to the dot when data is not fresh; null when fresh, syncing or errored. */
    val shortAge: String?,
    /** Spoken and tooltip text, always carries the exact age. */
    val description: String,
)

object SyncIndicators {
    /** A requested sync older than this is assumed lost (killed worker), not still running. */
    val SYNCING_WINDOW: Duration = Duration.ofMinutes(2)
    /** A failed user-requested run is shown for this long. */
    val ERROR_WINDOW: Duration = Duration.ofMinutes(10)
    private val FAILED = setOf(SyncOutcome.FAILED.name, SyncOutcome.NETWORK_ERROR.name, SyncOutcome.NEEDS_SIGN_IN.name)

    /** "now", "5m", "2h", "3d". */
    fun shortAge(age: Duration): String {
        val m = age.toMinutes()
        return when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            age.toHours() < 24 -> "${age.toHours()}h"
            else -> "${age.toDays()}d"
        }
    }

    fun exactAge(age: Duration): String = app.cairn.core.common.RelativeAge.format(age)

    /** [tier] and [age] come from the data (existing Fresh/Aging/Stale thresholds); [state] adds syncing and error. */
    fun derive(tier: app.cairn.core.model.Freshness, age: Duration, state: SyncState?, now: Instant): Indicator {
        val exact = exactAge(age)
        val req = state?.userSyncRequestedMs
        if (req != null && Duration.between(Instant.ofEpochMilli(req), now).let { !it.isNegative && it < SYNCING_WINDOW }) {
            return Indicator(IndicatorKind.Syncing, null, "Syncing. $exact")
        }
        val last = state?.runs?.firstOrNull()
        if (last != null && (last.outcome in FAILED || (last.outcome == SyncOutcome.SKIPPED.name && last.note?.contains("budget") == true)) && SyncReason.entries.firstOrNull { it.name == last.reason }?.userInitiated == true &&
            Duration.between(Instant.ofEpochMilli(last.atMs), now).let { !it.isNegative && it < ERROR_WINDOW }
        ) {
            return Indicator(IndicatorKind.Error, null, "Couldn't sync, tap to retry. $exact")
        }
        return when (tier) {
            app.cairn.core.model.Freshness.Fresh -> Indicator(IndicatorKind.Fresh, null, "$exact. Tap to sync")
            app.cairn.core.model.Freshness.Aging -> Indicator(IndicatorKind.Aging, shortAge(age), "$exact. Getting old. Tap to sync")
            app.cairn.core.model.Freshness.Stale -> Indicator(IndicatorKind.Stale, shortAge(age), "$exact. Stale. Tap to sync")
        }
    }

    /** Header line state in the app: dot colour kind plus the briefly shown "Up to date" confirmation. */
    fun justSucceeded(state: SyncState?, now: Instant, window: Duration = Duration.ofSeconds(4)): Boolean {
        val last = state?.runs?.firstOrNull() ?: return false
        return last.outcome == SyncOutcome.SUCCESS.name && Duration.between(Instant.ofEpochMilli(last.atMs), now).let { !it.isNegative && it < window }
    }
}

/** Header status text and error banner copy, kept pure so every state is testable. */
object StatusLine {
    /** Text next to the header dot; null when nothing needs saying (fresh data stays silent). */
    fun text(ind: Indicator, upToDate: Boolean): String? = when {
        ind.kind == IndicatorKind.Syncing -> "Syncing…"
        upToDate -> "Up to date"
        ind.kind == IndicatorKind.Aging || ind.kind == IndicatorKind.Stale -> ind.shortAge
        else -> null
    }

    /** Banner message for the newest failed user-requested run that was not dismissed; null otherwise. Never shows a retry gap. */
    fun errorMessage(state: SyncState?, now: Instant, dismissedAtMs: Long): String? {
        val r = state?.runs?.firstOrNull() ?: return null
        val user = SyncReason.entries.firstOrNull { it.name == r.reason }?.userInitiated == true
        val failed = r.outcome in setOf(SyncOutcome.FAILED.name, SyncOutcome.NETWORK_ERROR.name, SyncOutcome.NEEDS_SIGN_IN.name, SyncOutcome.PARTIAL.name) ||
            (r.outcome == SyncOutcome.SKIPPED.name && r.note?.contains("budget") == true)
        if (!user || !failed || r.atMs <= dismissedAtMs) return null
        if (Duration.between(Instant.ofEpochMilli(r.atMs), now) >= SyncIndicators.ERROR_WINDOW) return null
        return ManualSync.result(state, r.atMs)
    }
}
