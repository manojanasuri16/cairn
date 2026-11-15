package app.cairn.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
