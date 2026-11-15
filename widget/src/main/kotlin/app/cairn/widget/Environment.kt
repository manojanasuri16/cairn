package app.cairn.widget

import app.cairn.core.model.DisplayPrefs
import app.cairn.data.ActivityRepository
import app.cairn.data.DailyRepository
import app.cairn.data.RecoveryRepository
import app.cairn.data.SleepRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What widgets read. Room only, never the network. */
class WidgetDataSources(
    val daily: DailyRepository,
    val sleep: SleepRepository,
    val recovery: RecoveryRepository,
    val activities: ActivityRepository,
    val prefs: suspend () -> DisplayPrefs = { DisplayPrefs() },
    val auth: suspend () -> AuthState,
)

/** Installed by the app at start-up; widgets are instantiated by the system so they cannot be injected. */
object WidgetEnvironment {
    @Volatile var sources: WidgetDataSources? = null
        private set

    /** Emits on every [WidgetUpdater] run so freshness and "today" are recomputed even if no row changed. */
    val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also { it.tryEmit(Unit) }

    fun install(s: WidgetDataSources) { sources = s }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> build(block: (WidgetDataSources, LocalDate) -> Flow<WidgetUi<T>>): Flow<WidgetUi<T>> =
        ticks.onStart { emit(Unit) }.flatMapLatest {
            val s = sources
            if (s == null) flowOf(WidgetUi.Loading) else block(s, LocalDate.now())
        }

    fun today(): Flow<WidgetUi<TodayUi>> = build { s, day ->
        combine(s.daily.observeDay(day), s.sleep.observeLatest(day), s.recovery.observe()) { d, sl, r ->
            WidgetMapper.today(d?.value, sl?.value, r?.value, s.auth(), Instant.now(), day, prefs = s.prefs())
        }
    }

    fun recovery(): Flow<WidgetUi<RecoveryUi>> = build { s, _ ->
        s.recovery.observe().map { WidgetMapper.recovery(it?.value, s.auth(), Instant.now(), s.prefs()) }
    }

    fun lastActivity(): Flow<WidgetUi<ActivityUi>> = build { s, day ->
        s.activities.observeRecent(day.minusDays(60), 1).map { WidgetMapper.lastActivity(it.firstOrNull()?.value, s.auth(), Instant.now(), day, prefs = s.prefs()) }
    }
}
