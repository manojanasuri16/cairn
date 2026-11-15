package app.cairn.widget

import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.data.ActivityRepository
import app.cairn.data.DailyRepository
import app.cairn.data.ActivityDetailRepository
import app.cairn.data.FitnessRepository
import app.cairn.data.HrvRepository
import app.cairn.data.VitalsRepository
import app.cairn.data.TrainingLoadRepository
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
    val trainingLoad: TrainingLoadRepository,
    val fitness: FitnessRepository,
    /** Newer COROS data (heart rate, HRV, activity details). Null in tests that do not need them. */
    val vitals: VitalsRepository? = null,
    val hrv: HrvRepository? = null,
    val details: ActivityDetailRepository? = null,
    val prefs: suspend () -> DisplayPrefs = { DisplayPrefs() },
    /** Per-widget configuration and the app-wide appearance. Null in tests. */
    val settings: WidgetSettings? = null,
    val auth: suspend () -> AuthState,
    /** Persisted sync state, for the Syncing and error indicator. Null in tests. */
    val syncState: Flow<app.cairn.sync.SyncState>? = null,
)

/** Installed by the app at start-up; widgets are instantiated by the system so they cannot be injected. */
object WidgetEnvironment {
    @Volatile var sources: WidgetDataSources? = null
        private set

    /** Emits on every [WidgetUpdater] run so freshness and "today" are recomputed even if no row changed. */
    val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also { it.tryEmit(Unit) }

    fun install(s: WidgetDataSources) { sources = s }

    /** Set by the app: marks "syncing" and queues an expedited user sync. Widgets are created by the system so this is a hook. */
    @Volatile var requestSync: (suspend () -> Unit)? = null

    val settings: WidgetSettings? get() = sources?.settings

    private suspend fun WidgetDataSources.prefsWith(unit: DistanceUnit?): DisplayPrefs = prefs().let { if (unit == null) it else it.copy(unit = unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> build(block: (WidgetDataSources, LocalDate) -> Flow<WidgetUi<T>>): Flow<WidgetUi<T>> =
        ticks.onStart { emit(Unit) }.flatMapLatest {
            val s = sources
            if (s == null) flowOf(WidgetUi.Loading) else block(s, LocalDate.now())
        }

    fun today(unit: DistanceUnit? = null): Flow<WidgetUi<TodayUi>> = build { s, day ->
        combine(
            combine(s.daily.observeDay(day), s.sleep.observeLatest(day), s.recovery.observe()) { d, sl, r -> Triple(d, sl, r) },
            s.vitals?.observe(day, day) ?: flowOf(emptyList()),
        ) { (d, sl, r), v ->
            WidgetMapper.today(d?.value, sl?.value, r?.value, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit), vitals = v.lastOrNull()?.value)
        }
    }

    fun recovery(unit: DistanceUnit? = null): Flow<WidgetUi<RecoveryUi>> = build { s, _ ->
        s.recovery.observe().map { WidgetMapper.recovery(it?.value, s.auth(), Instant.now(), s.prefsWith(unit)) }
    }

    fun lastActivity(unit: DistanceUnit? = null): Flow<WidgetUi<ActivityUi>> = build { s, day ->
        s.activities.observeRecent(day.minusDays(60), 1).map { WidgetMapper.lastActivity(it.firstOrNull()?.value, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit)) }
    }

    fun trainingLoad(unit: DistanceUnit? = null): Flow<WidgetUi<TrainingLoadUi>> = build { s, day ->
        combine(s.trainingLoad.observe(day.minusDays(14), day), s.fitness.observe()) { loads, fit ->
            // No VO2max history is cached, so no trend yet (previousVo2max = null).
            WidgetMapper.trainingLoad(loads.map { it.value }, fit?.value, null, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit))
        }
    }

    fun sleep(unit: DistanceUnit? = null): Flow<WidgetUi<SleepUi>> = build { s, day ->
        s.sleep.observeLatest(day).map { WidgetMapper.sleep(it?.value, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit)) }
    }

    fun overview(unit: DistanceUnit? = null): Flow<WidgetUi<OverviewUi>> = build { s, day ->
        val first = combine(s.daily.observeDay(day), s.sleep.observeLatest(day), s.recovery.observe()) { d, sl, r -> Triple(d?.value, sl?.value, r?.value) }
        val second = combine(s.trainingLoad.observe(day.minusDays(14), day), s.fitness.observe(), s.activities.observeRecent(day.minusDays(60), 1)) { l, f, a -> Triple(l.map { it.value }, f?.value, a.firstOrNull()?.value) }
        val third = combine(
            s.vitals?.observe(day, day) ?: flowOf(emptyList()), s.hrv?.observe(day, day) ?: flowOf(emptyList()),
            s.activities.observeRecent(app.cairn.core.model.WeeklyMileage.mondayOf(day).minusDays(7), 500), s.details?.observeAll() ?: flowOf(emptyMap()),
        ) { v, h, acts, det -> Extras(v.lastOrNull()?.value, h.lastOrNull()?.value, acts.map { it.value }, det) }
        combine(first, second, third) { (d, sl, r), (l, f, a), x ->
            val prefs = s.prefsWith(unit); val now = Instant.now(); val auth = s.auth()
            OverviewMapper.build(
                WidgetMapper.today(d, sl, r, auth, now, day, prefs = prefs), WidgetMapper.sleep(sl, auth, now, day, prefs = prefs),
                WidgetMapper.recovery(r, auth, now, prefs), WidgetMapper.trainingLoad(l, f, null, auth, now, day, prefs = prefs),
                WidgetMapper.fitness(f, auth, now, prefs), WidgetMapper.lastActivity(a, auth, now, day, prefs = prefs),
                WidgetMapper.overviewExtras(
                    x.vitals, x.hrv, if (s.details != null) app.cairn.core.model.WeeklyLoad.compute(x.activities, x.details, day) else null,
                    (x.details.values.maxOfOrNull { it.stamp.sourceUpdatedAt } ?: x.activities.maxOfOrNull { it.stamp.sourceUpdatedAt }), x.activities.firstOrNull { it.date == day && (it.distanceMeters ?: 0.0) > 0 }, now, day, prefs,
                    mileage = app.cairn.core.model.WeeklyMileage.compute(x.activities, day), mileageStamp = x.activities.filter { !it.date.isBefore(app.cairn.core.model.WeeklyMileage.mondayOf(day)) }.maxOfOrNull { it.stamp.sourceUpdatedAt },
                ),
            )
        }
    }

    fun hrv(unit: DistanceUnit? = null): Flow<WidgetUi<HrvUi>> = build { s, day ->
        (s.hrv?.observe(day.minusDays(6), day) ?: flowOf(emptyList())).map { rows ->
            WidgetMapper.hrv(rows.map { it.value }, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit))
        }
    }

    fun heartRate(unit: DistanceUnit? = null): Flow<WidgetUi<HeartRateUi>> = build { s, day ->
        (s.vitals?.observe(day.minusDays(6), day) ?: flowOf(emptyList())).map { rows ->
            WidgetMapper.heartRate(rows.map { it.value }, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit))
        }
    }

    fun stress(unit: DistanceUnit? = null): Flow<WidgetUi<StressUi>> = build { s, day ->
        (s.vitals?.observe(day.minusDays(6), day) ?: flowOf(emptyList())).map { rows ->
            WidgetMapper.stress(rows.map { it.value }, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit))
        }
    }

    /** Two weeks of activities (this Monday to Sunday and the one before), so a week that spans two months is complete. */
    fun weeklyMileage(unit: DistanceUnit? = null): Flow<WidgetUi<MileageUi>> = build { s, day ->
        val since = app.cairn.core.model.WeeklyMileage.mondayOf(day).minusDays(7)
        s.activities.observeRecent(since, 500).map { rows ->
            WidgetMapper.weeklyMileage(rows.map { it.value }, s.auth(), Instant.now(), day, prefs = s.prefsWith(unit))
        }
    }

    fun fitness(unit: DistanceUnit? = null): Flow<WidgetUi<FitnessUi>> = build { s, _ ->
        s.fitness.observe().map { WidgetMapper.fitness(it?.value, s.auth(), Instant.now(), s.prefsWith(unit)) }
    }
}

private data class Extras(
    val vitals: app.cairn.core.model.VitalsDay?, val hrv: app.cairn.core.model.HrvDay?,
    val activities: List<app.cairn.core.model.Activity>, val details: Map<String, app.cairn.core.model.ActivityDetail>,
)
