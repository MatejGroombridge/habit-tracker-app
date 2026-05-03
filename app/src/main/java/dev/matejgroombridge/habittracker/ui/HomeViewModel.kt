package dev.matejgroombridge.habittracker.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Top-level UI state for [dev.matejgroombridge.habittracker.ui.screens.HomeScreen].
 */
data class HomeUiState(
    val habits: List<Habit> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
)

class HomeViewModel(
    private val repository: HabitRepository,
) : ViewModel() {

    private val today: Long get() = LocalDate.now().toEpochDay()

    val uiState: StateFlow<HomeUiState> = repository.habits
        .map { habits -> HomeUiState(habits = habits, todayEpochDay = today) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(todayEpochDay = today),
        )

    fun addHabit(name: String) {
        viewModelScope.launch { repository.addHabit(name, today) }
    }

    fun toggleToday(habitId: String) {
        viewModelScope.launch { repository.toggleCompletion(habitId, today) }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { repository.deleteHabit(habitId) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(HabitRepository(application.applicationContext))
            }
        }
    }
}
