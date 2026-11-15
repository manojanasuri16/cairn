package app.cairn.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val STATE_KEY = stringPreferencesKey("sync_state_json")
private val INTERVAL_KEY = intPreferencesKey("sync_interval_min")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun decode(text: String?): SyncState =
    if (text == null) SyncState() else runCatching { json.decodeFromString(SyncState.serializer(), text) }.getOrDefault(SyncState())

/** Sync state and settings in one Preferences DataStore (app-private, no secrets, no health values). */
class DataStoreSyncStateStore(private val ds: DataStore<Preferences>) : SyncStateStore {
    override val state: Flow<SyncState> = ds.data.map { decode(it[STATE_KEY]) }
    override suspend fun get(): SyncState = state.first()
    override suspend fun update(transform: (SyncState) -> SyncState): SyncState {
        var out = SyncState()
        ds.edit { p ->
            out = transform(decode(p[STATE_KEY]))
            p[STATE_KEY] = json.encodeToString(SyncState.serializer(), out)
        }
        return out
    }
}

class DataStoreSyncSettings(private val ds: DataStore<Preferences>) : SyncSettings {
    override val intervalMinutes: Flow<Int> = ds.data.map { SyncSettings.normalize(it[INTERVAL_KEY]) }
    override suspend fun interval(): Int = intervalMinutes.first()
    override suspend fun setInterval(minutes: Int) { ds.edit { it[INTERVAL_KEY] = SyncSettings.normalize(minutes) } }
}

private val NUDGE_ON = booleanPreferencesKey("stale_nudge_on")
private val NUDGE_HOURS = intPreferencesKey("stale_nudge_hours")
private val NUDGE_DAY = stringPreferencesKey("stale_nudge_last_day")

class DataStoreNudgeSettings(private val ds: DataStore<Preferences>) : NudgeSettings {
    override val enabled: Flow<Boolean> = ds.data.map { it[NUDGE_ON] ?: false }
    override val thresholdHours: Flow<Int> = ds.data.map { NudgeSettings.normalize(it[NUDGE_HOURS]) }
    override suspend fun isEnabled() = enabled.first()
    override suspend fun threshold() = thresholdHours.first()
    override suspend fun setEnabled(on: Boolean) { ds.edit { it[NUDGE_ON] = on } }
    override suspend fun setThreshold(hours: Int) { ds.edit { it[NUDGE_HOURS] = NudgeSettings.normalize(hours) } }
    override suspend fun lastNotifiedDay(): String? = ds.data.first()[NUDGE_DAY]
    override suspend fun setLastNotifiedDay(day: String?) { ds.edit { if (day == null) it.remove(NUDGE_DAY) else it[NUDGE_DAY] = day } }
}
