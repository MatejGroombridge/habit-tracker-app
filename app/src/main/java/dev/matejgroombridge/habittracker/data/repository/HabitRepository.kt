package dev.matejgroombridge.habittracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.habittracker.data.model.Habit
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
 * Room database, no migrations, just one JSON blob. If the schema needs to
 * evolve later, write a one-shot migration in [load].
 */
class HabitRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listSerializer = ListSerializer(Habit.serializer())

    val habits: Flow<List<Habit>> = context.habitsDataStore.data.map { prefs ->
        load(prefs[KEY_HABITS_JSON])
    }

    suspend fun addHabit(name: String, todayEpochDay: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            current + Habit(name = trimmed, createdAtEpochDay = todayEpochDay)
        }
    }

    suspend fun toggleCompletion(habitId: String, epochDay: Long) {
        update { current ->
            current.map { habit ->
                if (habit.id == habitId) habit.toggleCompletion(epochDay) else habit
            }
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
