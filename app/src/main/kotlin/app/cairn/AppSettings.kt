package app.cairn

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.widget.TapTarget
import app.cairn.widget.TapTargets
import app.cairn.widget.WidgetKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val UNIT = stringPreferencesKey("display_unit")
private val FRESH = intPreferencesKey("display_fresh_hours")
private val AGING = intPreferencesKey("display_aging_hours")
private val ONBOARDED = booleanPreferencesKey("onboarding_done")

/** App display preferences and the one-time onboarding flag, in the same app-private DataStore as the sync settings. No health values. */
class AppSettings(private val ds: DataStore<Preferences>) {
    val display: Flow<DisplayPrefs> = ds.data.map { DisplayPrefs.of(it[UNIT], it[FRESH], it[AGING]) }
    val onboardingDone: Flow<Boolean> = ds.data.map { it[ONBOARDED] ?: false }

    /** Where each widget's body tap goes; widgets without a stored choice use [TapTargets.default]. */
    fun tapTarget(kind: WidgetKind): Flow<TapTarget> = ds.data.map { TapTargets.decode(it[stringPreferencesKey(TapTargets.storageKey(kind))], kind) }
    val tapTargets: Flow<Map<WidgetKind, TapTarget>> = ds.data.map { p ->
        WidgetKind.entries.associateWith { TapTargets.decode(p[stringPreferencesKey(TapTargets.storageKey(it))], it) }
    }
    suspend fun setTapTarget(kind: WidgetKind, target: TapTarget) { ds.edit { it[stringPreferencesKey(TapTargets.storageKey(kind))] = TapTargets.encode(target) } }

    suspend fun displayNow(): DisplayPrefs = display.first()
    suspend fun setUnit(u: DistanceUnit) { ds.edit { it[UNIT] = u.name } }
    suspend fun setFreshHours(h: Int) { ds.edit { it[FRESH] = h } }
    suspend fun setAgingHours(h: Int) { ds.edit { it[AGING] = h } }
    suspend fun setOnboardingDone(done: Boolean) { ds.edit { it[ONBOARDED] = done } }
}
