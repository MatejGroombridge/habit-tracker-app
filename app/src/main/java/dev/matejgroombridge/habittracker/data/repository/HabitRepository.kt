package dev.matejgroombridge.habittracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.habitsDataStore: DataStore<Preferences> by preferencesDataStore(name = "habits")

/**
 * Single source of truth for the user's habits. Backed by a Preferences
 * DataStore that stores the habit list as a JSON-encoded string under one key.
 *
 * For a personal-scale habit tracker this is intentionally simple — there's no
 * Room database, no migrations, just one JSON blob. Adding new fields to
 * [Habit] is safe because the JSON parser is configured with
 * `ignoreUnknownKeys = true` and every new field has a default value.
 */
class HabitRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Permit defaults to be omitted in serialized form for older blobs.
        isLenient = true
    }

    private val listSerializer = ListSerializer(Habit.serializer())

    val habits: Flow<List<Habit>> = context.habitsDataStore.data.map { prefs ->
        load(prefs[KEY_HABITS_JSON])
    }

    /**
     * Creates a new habit. All optional fields default to sensible values
     * matching [Habit]'s defaults.
     */
    suspend fun addHabit(
        name: String,
        todayEpochDay: Long,
        description: String = "",
        iconKey: String = Habit.DEFAULT_ICON_KEY,
        colorKey: String = Habit.DEFAULT_COLOR_KEY,
        frequency: HabitFrequency = HabitFrequency.Daily,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            current + Habit(
                name = trimmed,
                description = description.trim(),
                iconKey = iconKey,
                colorKey = colorKey,
                frequency = frequency,
                createdAtEpochDay = todayEpochDay,
            )
        }
    }

    /** Replace mutable fields on an existing habit. Completion history and id are preserved. */
    suspend fun updateHabit(
        habitId: String,
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        frequency: HabitFrequency,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        update { current ->
            current.map { h ->
                if (h.id == habitId) {
                    h.copy(
                        name = trimmedName,
                        description = description.trim(),
                        iconKey = iconKey,
                        colorKey = colorKey,
                        frequency = frequency,
                    )
                } else h
            }
        }
    }

    suspend fun setCompleted(habitId: String, epochDay: Long, completed: Boolean) {
        update { current ->
            current.map { h ->
                if (h.id != habitId) h
                else if (completed) h.markCompleted(epochDay) else h.markNotCompleted(epochDay)
            }
        }
    }

    suspend fun toggleCompletion(habitId: String, epochDay: Long) {
        update { current ->
            current.map { habit ->
                if (habit.id == habitId) habit.toggleCompletion(epochDay) else habit
            }
        }
    }

    suspend fun setArchived(habitId: String, archived: Boolean) {
        update { current ->
            current.map { h -> if (h.id == habitId) h.copy(archived = archived) else h }
        }
    }

    suspend fun deleteHabit(habitId: String) {
        update { current -> current.filterNot { it.id == habitId } }
    }

    private suspend fun update(block: (List<Habit>) -> List<Habit>) {
        context.habitsDataStore.edit { prefs ->
            val existing = load(prefs[KEY_HABITS_JSON])
            val updated = block(existing)
            prefs[KEY_HABITS_JSON] = json.encodeToString(listSerializer, updated)
        }
    }

    private fun load(raw: String?): List<Habit> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        val KEY_HABITS_JSON = stringPreferencesKey("habits_json")
    }
}
