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

    /** Decides before enqueueing, so a skipped tap is never silent. */
    fun gate(state: SyncState, now: Instant, signedIn: Boolean, online: Boolean, zone: ZoneId = ZoneId.systemDefault()): Gate {
        if (!signedIn) return Gate.Skipped("Not signed in to COROS. Sign in under COROS account, then try again.")
        if (!online) return Gate.Skipped("You are offline. Connect to the internet and try again.")
        val last = state.lastMcpRunMs
        if (last != null && !McpPlanner.gapElapsed(SyncReason.MANUAL, state, now)) {
            val at = Instant.ofEpochMilli(last).plus(McpPlanner.MANUAL_GAP)
            val mins = Duration.between(now, at).coerceAtLeast(Duration.ZERO).let { (it.seconds + 59) / 60 }.coerceAtLeast(1)
            val time = LocalTime.ofInstant(at, zone).let { "%02d:%02d".format(it.hour, it.minute) }
            return Gate.Skipped("Synced a moment ago. You can sync again in $mins min (at $time).", at.toEpochMilli())
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
            SyncOutcome.SKIPPED.name -> "Nothing to do: ${r.note ?: "no source available"}."
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
