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
import kotlinx.coroutines.flow.first
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
        skipsPerWeek: Int = -1,
        includeInReminders: Boolean? = null,
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
                        // -1 sentinel = "unchanged" so legacy callers don't need to pass it.
                        skipsPerWeek = if (skipsPerWeek < 0) h.skipsPerWeek else skipsPerWeek.coerceIn(0, 7),
                        includeInReminders = includeInReminders ?: h.includeInReminders,
                    )
                } else h
            }
        }
    }

    /** Toggle whether [habitId] should appear in daily reminder notifications. */
    suspend fun setIncludeInReminders(habitId: String, include: Boolean) {
        update { current ->
            current.map { h -> if (h.id == habitId) h.copy(includeInReminders = include) else h }
        }
    }

    /** Toggle today's "skip" marker for [habitId]. Skips replace any completion on the same day. */
    suspend fun setSkipped(habitId: String, epochDay: Long, skipped: Boolean) {
        update { current ->
            current.map { h ->
                if (h.id != habitId) return@map h
                if (skipped) {
                    h.copy(
                        skippedDays = h.skippedDays + epochDay,
                        completedDays = h.completedDays - epochDay,
                    )
                } else {
                    h.copy(skippedDays = h.skippedDays - epochDay)
                }
            }
        }
    }

    /** Pause / unpause a habit. Pausing freezes streaks; unpausing clears the marker. */
    suspend fun setPaused(habitId: String, paused: Boolean, todayEpochDay: Long) {
        update { current ->
            current.map { h ->
                if (h.id != habitId) h
                else h.copy(pausedSinceEpochDay = if (paused) todayEpochDay else null)
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

    /**
     * Reorder the active (non-archived) habits to match [orderedActiveIds].
     * Archived habits keep their relative order and are appended after the
     * reordered active ones, mirroring how the Today screen filters them
     * out anyway.
     *
     * IDs in [orderedActiveIds] that don't correspond to a current habit are
     * silently ignored, and any active habit not present in the list is
     * appended in its previous relative order — this makes the call
     * idempotent and tolerant of rapid drag updates.
     */
    suspend fun setOrdering(orderedActiveIds: List<String>) {
        update { current ->
            val byId = current.associateBy { it.id }
            val activeOrdered = orderedActiveIds.mapNotNull { byId[it] }
                .filterNot { it.archived }
            val activeOrderedIds = activeOrdered.map { it.id }.toSet()
            val activeRemaining = current.filterNot { it.archived || it.id in activeOrderedIds }
            val archived = current.filter { it.archived }
            activeOrdered + activeRemaining + archived
        }
    }

    /**
     * Serialise the current habit list to a JSON string suitable for export
     * to a file. Encodes defaults so older versions of the app round-trip
     * cleanly.
     */
    suspend fun exportJson(): String {
        val prefs = context.habitsDataStore.data.first()
        val current = load(prefs[KEY_HABITS_JSON])
        return json.encodeToString(listSerializer, current)
    }

    /**
     * Replace the habit list with the contents of [rawJson]. Returns the
     * number of habits imported, or `null` if the JSON couldn't be parsed
     * (the existing list is left untouched in that case).
     */
    suspend fun importJson(rawJson: String): Int? {
        val parsed = runCatching { json.decodeFromString(listSerializer, rawJson) }.getOrNull()
            ?: return null
        update { parsed }
        return parsed.size
    }

    private suspend fun update(block: (List<Habit>) -> List<Habit>) {
        context.habitsDataStore.edit { prefs ->
            val existing = load(prefs[KEY_HABITS_JSON])
            val updated = block(existing)
            prefs[KEY_HABITS_JSON] = json.encodeToString(listSerializer, updated)
        }
        // Tell every widget to redraw — completion state, name, colour, and
        // archived state can all change here. Cheap broadcast; the receiver
        // does the actual rendering off the main thread.
        runCatching {
            dev.matejgroombridge.habittracker.widget.HabitWidgetReceiver.broadcastRefresh(context)
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
