package dev.matejgroombridge.habittracker.data.settings

import dev.matejgroombridge.habittracker.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When false (the default), habit cards do not display the streak counter. */
    val showStreaks: Boolean = false,
)
