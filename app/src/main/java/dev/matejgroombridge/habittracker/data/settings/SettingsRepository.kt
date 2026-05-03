package dev.matejgroombridge.habittracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.habittracker.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Single source of truth for user preferences. Backed by a Preferences
 * DataStore — one [Preferences.Key] per setting, mapped into a [Settings]
 * snapshot for the UI to consume.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[KEY_THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
            showStreaks = prefs[KEY_SHOW_STREAKS] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setShowStreaks(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_STREAKS] = show
        }
    }

    private fun parseThemeMode(raw: String): ThemeMode = runCatching {
        ThemeMode.valueOf(raw)
    }.getOrDefault(ThemeMode.System)

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SHOW_STREAKS = booleanPreferencesKey("show_streaks")
    }
}
