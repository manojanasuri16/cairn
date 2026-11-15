package app.cairn.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.CairnApp
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.WeeklyLoad
import app.cairn.source.healthconnect.HcAvailability
import app.cairn.source.healthconnect.syncReadPermissions
import app.cairn.sync.ManualSync
import app.cairn.sync.SyncReason
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Re-evaluates "now" every minute so freshness tiers and "Updated 5m ago" keep moving while the screen is open. */
private fun minuteTicks(): Flow<Unit> = flow { while (true) { emit(Unit); delay(60_000) } }

private fun <T> Flow<T>.hot(scope: kotlinx.coroutines.CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)

data class RefreshResult(val message: String, val positive: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph

    val state: StateFlow<TodayState> = minuteTicks().flatMapLatest {
        val today = LocalDate.now()
        val base = combine(
            graph.dailyRepository.observe(today.minusDays(6), today),
            graph.sleepRepository.observe(today.minusDays(7), today),
            graph.recoveryRepository.observe(),
            graph.trainingLoadRepository.observe(today.minusDays(6), today),
            graph.appSettings.display,
        ) { d, s, r, l, p -> TodayBase(d.map { it.value }, s.map { it.value }, r?.value, l.map { it.value }, p) }
        val more = combine(
            graph.vitalsRepository.observe(today.minusDays(6), today),
            graph.hrvRepository.observe(today.minusDays(1), today),
            graph.activityRepository.observeRecent(today.minusDays(7), 100),
            graph.activityDetailRepository.observeAll(),
            graph.fitnessRepository.observe(),
        ) { v, h, a, det, f -> TodayExtras(v.map { it.value }, h.map { it.value }, a.map { it.value }, det, f?.value) }
        combine(base, more) { b, x ->
            AppMapper.today(b.daily, b.sleeps, b.recovery, b.load, graph.authState(), Instant.now(), today, b.prefs, extras = x)
        }
    }.hot(viewModelScope, TodayState())
}

private data class TodayBase(
    val daily: List<app.cairn.core.model.DailySummary>, val sleeps: List<app.cairn.core.model.SleepNight>,
    val recovery: app.cairn.core.model.Recovery?, val load: List<app.cairn.core.model.TrainingLoadDay>, val prefs: DisplayPrefs,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    val state: StateFlow<ActivitiesState> = minuteTicks().flatMapLatest {
        val today = LocalDate.now()
        // The current and the previous month, like the COROS "All activities" list.
        val from = today.withDayOfMonth(1).minusMonths(1)
        combine(graph.activityRepository.observeRecent(from, 200), graph.appSettings.display) { a, p ->
            AppMapper.activities(a.map { it.value }, graph.authState(), Instant.now(), today, p)
        }
    }.hot(viewModelScope, ActivitiesState())
}

@OptIn(ExperimentalCoroutinesApi::class)
class FitnessViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    val state: StateFlow<FitnessState> = minuteTicks().flatMapLatest {
        val today = LocalDate.now()
        combine(
            graph.trainingLoadRepository.observe(today.minusDays(6), today), graph.fitnessRepository.observe(), graph.appSettings.display,
            graph.activityRepository.observeRecent(today.minusDays(7), 100), graph.activityDetailRepository.observeAll(),
        ) { l, f, p, a, det ->
            val week = WeeklyLoad.compute(a.map { it.value }, det, today)
            AppMapper.fitness(l.map { it.value }, f?.value, graph.authState(), Instant.now(), today, p, weeklyTotal = week.total.takeIf { week.activityCount - week.missing > 0 })
        }
    }.hot(viewModelScope, FitnessState())
}

data class SettingsState(
    val display: DisplayPrefs = DisplayPrefs(),
    val intervalMinutes: Int = 30,
    val hc: HcStatus = HcStatus.Unknown,
    val corosSignedIn: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    private val hc = MutableStateFlow(HcStatus.Unknown)
    private val extra = MutableStateFlow(Pair(false, false)) // deleting, deleted
    private val signedIn = MutableStateFlow(false)

    val state: StateFlow<SettingsState> = combine(graph.appSettings.display, graph.syncSettings.intervalMinutes, hc, signedIn, extra) { d, i, h, s, e ->
        SettingsState(d, i, h, s, e.first, e.second)
    }.hot(viewModelScope, SettingsState())

    /** Re-reads the Health Connect permission state and sign-in; call on resume (the user may have changed them elsewhere). */
    fun refreshSources() {
        viewModelScope.launch {
            signedIn.value = withContext(Dispatchers.IO) { graph.tokenStore.load() != null }
            val reader = graph.healthConnect
            val avail: HcAvailability = reader.availability()
            val granted = if (avail == HcAvailability.Available) runCatching { reader.grantedPermissions() }.getOrNull() else emptySet()
            hc.value = SourceStatus.healthConnect(avail, granted, syncReadPermissions)
        }
    }

    fun onPermissionResult() { refreshSources(); graph.onPermissionChanged() }

    fun setUnit(u: DistanceUnit) = viewModelScope.launch { graph.appSettings.setUnit(u); graph.refreshWidgets() }
    fun setFresh(h: Int) = viewModelScope.launch { graph.appSettings.setFreshHours(h); graph.refreshWidgets() }
    fun setAging(h: Int) = viewModelScope.launch { graph.appSettings.setAgingHours(h); graph.refreshWidgets() }
    fun setInterval(min: Int) = viewModelScope.launch { runCatching { graph.syncScheduler.setInterval(min) } }

    fun deleteAll() {
        if (extra.value.first) return
        extra.value = true to false
        viewModelScope.launch {
            runCatching { graph.deleteAllData() }
            extra.value = false to true
        }
    }

    fun clearDeletedNotice() { extra.value = false to false }
}

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as CairnApp).graph
    /** Null until the stored flag is read, so a returning user never sees a flash of onboarding. */
    val done: StateFlow<Boolean?> = graph.appSettings.onboardingDone.map<Boolean, Boolean?> { it }.hot(viewModelScope, null)
    fun finish() { viewModelScope.launch { graph.appSettings.setOnboardingDone(true) } }
    fun onPermissionResult() { graph.onPermissionChanged() }
}

