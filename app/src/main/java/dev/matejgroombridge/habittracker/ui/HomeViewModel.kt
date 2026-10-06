package dev.matejgroombridge.habittracker.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitBackfill
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.data.stats.HabitStats
import dev.matejgroombridge.habittracker.data.stats.StatsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
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
    /**
     * The day the user's oldest habit — archived ones included — was
     * created, or `null` if they have none yet. The habit editor offers to
     * backfill a habit's history back to this day so a late starter doesn't
     * sit in the All Time grid as a mostly-empty row.
     */
    val oldestHabitEpochDay: Long? = null,
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
                oldestHabitEpochDay = HabitBackfill.earliestCreation(habits),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(todayEpochDay = today),
        )

    /**
     * Stats tab data, plus the Today screen's "Perfect day #N". Recomputed
     * on every change off the main thread — it walks every day of history.
     */
    val stats: StateFlow<StatsSnapshot> = repository.habits
        .map { habits -> HabitStats.compute(habits, today) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsSnapshot.empty(today),
        )

    fun addHabit(
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        frequency: HabitFrequency,
        /**
         * When non-null, backdate the habit to [HomeUiState.oldestHabitEpochDay]
         * and fill in synthetic history at roughly this success rate.
         */
        backfillPercent: Int? = null,
    ) {
        viewModelScope.launch {
            repository.addHabit(
                name = name,
                todayEpochDay = today,
                description = description,
                iconKey = iconKey,
                colorKey = colorKey,
                frequency = frequency,
                backfillPercent = backfillPercent,
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
        skipsPerWeek: Int = -1,
        /**
         * When non-null, also extend the habit's history back to the day the
         * user's oldest habit started, at roughly this success rate. Additive
         * — the days the habit already has recorded are left alone.
         */
        backfillPercent: Int? = null,
    ) {
        viewModelScope.launch {
            repository.updateHabit(
                habitId, name, description, iconKey, colorKey, frequency, skipsPerWeek,
            )
            // Sequenced inside the same coroutine so the generated days
            // honour the frequency that was just saved.
            if (backfillPercent != null) repository.backfillHistory(habitId, backfillPercent)
        }
    }

    fun toggleToday(habitId: String) {
        viewModelScope.launch { repository.toggleCompletion(habitId, today) }
    }

    fun setCompleted(habitId: String, epochDay: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(habitId, epochDay, completed) }
    }

    fun setSkipped(habitId: String, epochDay: Long, skipped: Boolean) {
        viewModelScope.launch { repository.setSkipped(habitId, epochDay, skipped) }
    }

    fun setPaused(habitId: String, paused: Boolean) {
        viewModelScope.launch { repository.setPaused(habitId, paused, today) }
    }

    /** Toggle whether [habitId] should be included in daily reminder notifications. */
    fun setIncludeInReminders(habitId: String, include: Boolean) {
        viewModelScope.launch { repository.setIncludeInReminders(habitId, include) }
    }

    /**
     * Returns the names of currently-active habits whose frequency is not
     * Daily. Used to populate the "Are you sure?" confirmation when the
     * user enables the global daily-habits-only toggle.
     */
    fun nonDailyActiveHabitNames(): List<String> = uiState.value.activeHabits
        .filter { it.frequency !is dev.matejgroombridge.habittracker.data.model.HabitFrequency.Daily }
        .map { it.name }

    /**
     * Archives every non-daily active habit in one batch. Idempotent — if
     * called when there are no non-daily habits the underlying setArchived
     * calls all become no-ops.
     */
    fun archiveNonDailyHabits() {
        viewModelScope.launch {
            uiState.value.activeHabits
                .filter { it.frequency !is dev.matejgroombridge.habittracker.data.model.HabitFrequency.Daily }
                .forEach { repository.setArchived(it.id, true, today) }
        }
    }

    fun setArchived(habitId: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(habitId, archived, today) }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { repository.deleteHabit(habitId) }
    }

    /** Reorder the active habits to match [orderedActiveIds]. */
    fun setOrdering(orderedActiveIds: List<String>) {
        viewModelScope.launch { repository.setOrdering(orderedActiveIds) }
    }

    /** Returns the JSON of the user's current habit list, or null if the call fails. */
    suspend fun exportJson(): String? = runCatching { repository.exportJson() }.getOrNull()

    /** Imports the JSON, returning the count of imported habits, or null on failure. */
    suspend fun importJson(rawJson: String): Int? = repository.importJson(rawJson)

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(HabitRepository(application.applicationContext))
            }
        }
    }
}
