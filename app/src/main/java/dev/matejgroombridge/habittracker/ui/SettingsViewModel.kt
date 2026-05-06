package dev.matejgroombridge.habittracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.habittracker.data.settings.NfcAction
import dev.matejgroombridge.habittracker.data.settings.Settings
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import dev.matejgroombridge.habittracker.data.settings.WeekStart
import dev.matejgroombridge.habittracker.notifications.ReminderScheduler
import dev.matejgroombridge.habittracker.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { repository.setAmoled(enabled) }
    }

    fun setNfcAction(action: NfcAction) {
        viewModelScope.launch { repository.setNfcAction(action) }
    }

    fun setWeekStart(weekStart: WeekStart) {
        viewModelScope.launch { repository.setWeekStart(weekStart) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderEnabled(enabled)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setReminderTimesPerDay(times: Int) {
        viewModelScope.launch {
            repository.setReminderTimesPerDay(times)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setReminderFirstTime(time: String) {
        viewModelScope.launch {
            repository.setReminderFirstTime(time)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setReminderLastTime(time: String) {
        viewModelScope.launch {
            repository.setReminderLastTime(time)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setSwipeToNavigate(enabled: Boolean) {
        viewModelScope.launch { repository.setSwipeToNavigate(enabled) }
    }

    fun setAllowSkips(enabled: Boolean) {
        viewModelScope.launch { repository.setAllowSkips(enabled) }
    }

    fun setAllowPauses(enabled: Boolean) {
        viewModelScope.launch { repository.setAllowPauses(enabled) }
    }

    /**
     * Sets the dailyHabitsOnly toggle. When [enabled] is true, the caller
     * is responsible for archiving any non-daily habits — see
     * [HomeViewModel.archiveNonDailyHabits], invoked from the confirmation
     * dialog in SettingsScreen.
     */
    fun setDailyHabitsOnly(enabled: Boolean) {
        viewModelScope.launch { repository.setDailyHabitsOnly(enabled) }
    }

    /**
     * Toggle Zen mode. When on, the app's UI is locked to a single
     * tap-to-complete view; the only other reachable thing is this
     * setting itself. See [Settings.zenMode] for the full definition.
     */
    fun setZenMode(enabled: Boolean) {
        viewModelScope.launch { repository.setZenMode(enabled) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                SettingsViewModel(ctx, SettingsRepository(ctx))
            }
        }
    }
}
