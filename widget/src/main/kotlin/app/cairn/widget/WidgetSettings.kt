package app.cairn.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cairn.core.designsystem.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where per-widget configuration and the app-wide appearance live. Stores choices only, nothing from the user's health data. */
class WidgetSettings(private val ds: DataStore<Preferences>) {
    private fun key(id: Int) = stringPreferencesKey("wcfg_$id")

    /** Stored config for one placed widget; defaults (inherit everything) when none was saved. */
    fun config(appWidgetId: Int, kind: WidgetKind): Flow<WidgetConfig> = ds.data.map { WidgetConfig.decode(it[key(appWidgetId)], kind) }

    suspend fun configNow(appWidgetId: Int, kind: WidgetKind): WidgetConfig = config(appWidgetId, kind).first()

    suspend fun save(appWidgetId: Int, config: WidgetConfig) { ds.edit { it[key(appWidgetId)] = config.encode() } }

    private fun shownKey(id: Int) = androidx.datastore.preferences.core.booleanPreferencesKey("wshown_$id")

    /** Whether this widget's configuration screen has already been shown (so a pin never opens it twice). */
    suspend fun configShown(appWidgetId: Int): Boolean = ds.data.first()[shownKey(appWidgetId)] ?: false
    suspend fun markConfigShown(appWidgetId: Int) { ds.edit { it[shownKey(appWidgetId)] = true } }

    /** Called when the launcher removes widgets. */
    suspend fun clear(ids: IntArray) { ds.edit { p -> ids.forEach { p.remove(key(it)); p.remove(shownKey(it)) } } }

    val appearance: Flow<AppAppearance> = ds.data.map { p ->
        AppAppearance(
            theme = ThemeId.parse(p[THEME]) ?: ThemeId.Default,
            bg = Bg.parse(p[BG]) ?: Bg.Gradient,
            opacity = (p[OPACITY] ?: 100).coerceIn(0, 100),
        )
    }

    suspend fun setTheme(t: ThemeId) { ds.edit { it[THEME] = t.name } }
    suspend fun setDefaultBackground(bg: Bg, opacity: Int) { ds.edit { it[BG] = bg.name; it[OPACITY] = opacity.coerceIn(0, 100) } }

    companion object {
        /** Default widget style that new and unconfigured widgets inherit (the app's own accent is separate). */
        val THEME = stringPreferencesKey("widget_default_style")
        val BG = stringPreferencesKey("widget_default_bg")
        val OPACITY = intPreferencesKey("widget_default_opacity")
    }
}
