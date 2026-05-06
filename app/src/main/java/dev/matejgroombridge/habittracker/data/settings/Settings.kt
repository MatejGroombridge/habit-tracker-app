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
    /**
     * When `true` the user can swipe horizontally between Today / Past Week
     * / All Time. When `false` the pager only responds to bottom-bar taps,
     * which is useful for users who find swipe gestures conflict with
     * other UI like the All-Time grid scroll.
     */
    val swipeToNavigate: Boolean = true,
    /**
     * Master switch for the per-day Skip action. When `false`, the
     * "Skip today" icon is hidden from the habit overview dialog and the
     * Past Week long-press menu, and the [Habit.skipsPerWeek] field is
     * ignored. Existing skipped days remain in storage so toggling back
     * on restores the previous behaviour with no data loss.
     */
    val allowSkips: Boolean = true,
    /**
     * Master switch for habit pausing. When `false`, the Pause icon is
     * hidden from the habit overview dialog and the Past Week long-press
     * menu. As with [allowSkips], existing pause state is preserved in
     * storage; toggling back on restores it untouched.
     */
    val allowPauses: Boolean = true,
    /**
     * When `true` the app treats only Daily-frequency habits as visible.
     * Any habit whose frequency is Weekly / TimesPerWeek / EveryNDays is
     * automatically archived. Toggling back to `false` does not unarchive
     * — the user can do that manually from the Archive screen, since some
     * may have intentionally cleaned house in the meantime.
     */
    val dailyHabitsOnly: Boolean = false,
    /**
     * "Zen mode" — collapses the entire app down to a single screen of
     * habit cards that can be tapped to mark complete. Hides:
     *   * The bottom navigation (Today is the only page).
     *   * The FAB and "Create" entry points.
     *   * The settings/archive icons in the Today top-app-bar.
     *   * Long-press habit overview, edit dialog, and any other modals.
     *   * Past Week, All Time, Reorder, Archived screens.
     *   * The home-screen widget's tap-to-complete? — no, the widget is
     *     external and continues to work; only in-app surfaces lock.
     *
     * The only other thing the user can do while Zen mode is on is open
     * Settings (via the gear icon in the bottom corner) and toggle Zen
     * back off again. All other settings rows are hidden.
     */
    val zenMode: Boolean = false,
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
