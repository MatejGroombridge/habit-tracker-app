package dev.matejgroombridge.habittracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
            amoled = prefs[KEY_AMOLED] ?: false,
            nfcAction = prefs[KEY_NFC_ACTION]?.let(::parseNfcAction) ?: NfcAction.Default,
            weekStart = prefs[KEY_WEEK_START]?.let(::parseWeekStart) ?: WeekStart.Default,
            reminders = ReminderSettings(
                enabled = prefs[KEY_REMINDER_ENABLED] ?: false,
                timesPerDay = (prefs[KEY_REMINDER_TIMES_PER_DAY] ?: 1).coerceIn(1, 6),
                firstTime = prefs[KEY_REMINDER_FIRST_TIME] ?: "09:00",
                lastTime = prefs[KEY_REMINDER_LAST_TIME] ?: "20:00",
            ),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAmoled(amoled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AMOLED] = amoled }
    }

    suspend fun setNfcAction(action: NfcAction) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_NFC_ACTION] = action.name }
    }

    suspend fun setWeekStart(weekStart: WeekStart) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_WEEK_START] = weekStart.name }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTimesPerDay(timesPerDay: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_REMINDER_TIMES_PER_DAY] = timesPerDay.coerceIn(1, 6)
        }
    }

    suspend fun setReminderFirstTime(time: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_REMINDER_FIRST_TIME] = time }
    }

    suspend fun setReminderLastTime(time: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_REMINDER_LAST_TIME] = time }
    }

    private fun parseThemeMode(raw: String): ThemeMode = runCatching {
        ThemeMode.valueOf(raw)
    }.getOrDefault(ThemeMode.System)

    private fun parseNfcAction(raw: String): NfcAction = runCatching {
        NfcAction.valueOf(raw)
    }.getOrDefault(NfcAction.Default)

    private fun parseWeekStart(raw: String): WeekStart = runCatching {
        WeekStart.valueOf(raw)
    }.getOrDefault(WeekStart.Default)

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_NFC_ACTION = stringPreferencesKey("nfc_action")
        val KEY_WEEK_START = stringPreferencesKey("week_start")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_TIMES_PER_DAY = intPreferencesKey("reminder_times_per_day")
        val KEY_REMINDER_FIRST_TIME = stringPreferencesKey("reminder_first_time")
        val KEY_REMINDER_LAST_TIME = stringPreferencesKey("reminder_last_time")
    }
}
