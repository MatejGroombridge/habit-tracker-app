package dev.matejgroombridge.habittracker.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Top-level UI state shared by every screen that lists habits. Splits the
 * habit list into active vs archived so each screen can take only what it
 * needs without re-filtering.
 */
data class HomeUiState(
    val activeHabits: List<Habit> = emptyList(),
    val archivedHabits: List<Habit> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
)

class HomeViewModel(
    private val repository: HabitRepository,
) : ViewModel() {

    private val today: Long get() = LocalDate.now().toEpochDay()

    val uiState: StateFlow<HomeUiState> = repository.habits
        .map { habits ->
            HomeUiState(
                activeHabits = habits.filterNot { it.archived },
                archivedHabits = habits.filter { it.archived },
                todayEpochDay = today,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(todayEpochDay = today),
        )

    fun addHabit(
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        frequency: HabitFrequency,
    ) {
        viewModelScope.launch {
            repository.addHabit(
                name = name,
                todayEpochDay = today,
                description = description,
                iconKey = iconKey,
                colorKey = colorKey,
                frequency = frequency,
            )
        }
    }

    fun updateHabit(
        habitId: String,
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        frequency: HabitFrequency,
    ) {
        viewModelScope.launch {
            repository.updateHabit(habitId, name, description, iconKey, colorKey, frequency)
        }
    }

    fun toggleToday(habitId: String) {
        viewModelScope.launch { repository.toggleCompletion(habitId, today) }
    }

    fun setCompleted(habitId: String, epochDay: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(habitId, epochDay, completed) }
    }

    fun setArchived(habitId: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(habitId, archived) }
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
