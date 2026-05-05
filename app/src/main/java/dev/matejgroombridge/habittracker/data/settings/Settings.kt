package dev.matejgroombridge.habittracker.data.settings

import dev.matejgroombridge.habittracker.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When [ThemeMode] resolves to dark, render with pure black backgrounds. */
    val amoled: Boolean = false,
    val nfcAction: NfcAction = NfcAction.Default,
    val weekStart: WeekStart = WeekStart.Default,
    val reminders: ReminderSettings = ReminderSettings(),
)

/**
 * User configuration for daily reminder notifications. Reminders are local
 * (no server / network) and scheduled on the device via AlarmManager. See
 * `ReminderScheduler` for the runtime side.
 *
 * @param enabled       Master switch. When `false` no notifications fire.
 * @param timesPerDay   How many evenly-spaced reminders to send per day, 1..6.
 * @param firstTime     "HH:MM"-formatted local time of the first reminder.
 * @param lastTime      "HH:MM" of the last reminder (only used when [timesPerDay] > 1).
 *                      Reminders 2..N-1 are spaced linearly between first and last.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val timesPerDay: Int = 1,
    val firstTime: String = "09:00",
    val lastTime: String = "20:00",
)
