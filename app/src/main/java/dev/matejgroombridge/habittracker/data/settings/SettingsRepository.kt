package dev.matejgroombridge.habittracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
            nfcAction = prefs[KEY_NFC_ACTION]?.let(::parseNfcAction) ?: NfcAction.Default,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setNfcAction(action: NfcAction) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NFC_ACTION] = action.name
        }
    }

    private fun parseThemeMode(raw: String): ThemeMode = runCatching {
        ThemeMode.valueOf(raw)
    }.getOrDefault(ThemeMode.System)

    private fun parseNfcAction(raw: String): NfcAction = runCatching {
        NfcAction.valueOf(raw)
    }.getOrDefault(NfcAction.Default)

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_NFC_ACTION = stringPreferencesKey("nfc_action")
    }
}
