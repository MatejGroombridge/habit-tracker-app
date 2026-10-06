package dev.matejgroombridge.habittracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitBackfill
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

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

    // Every reader (app, widget, reminders, NFC) comes through here, so the
    // legacy migration runs before anyone sees a habit list.
    val habits: Flow<List<Habit>> = context.habitsDataStore.data
        .onStart { migrateLegacyInverseHabits() }
        .map { prefs -> load(prefs[KEY_HABITS_JSON]) }

    /**
     * Creates a new habit. All optional fields default to sensible values
     * matching [Habit]'s defaults.
     *
     * When [backfillPercent] is non-null the habit is backdated to the day
     * the user's oldest existing habit was created and given synthetic
     * history at roughly that success rate, so it doesn't sit in the All
     * Time grid as an empty row next to habits that go back months. The
     * start day is resolved here rather than passed in so it always reflects
     * the list as stored. With no existing habits there's nothing to line up
     * with, so the flag is ignored and the habit starts today as usual.
     */
    suspend fun addHabit(
        name: String,
        todayEpochDay: Long,
        description: String = "",
        iconKey: String = Habit.DEFAULT_ICON_KEY,
        colorKey: String = Habit.DEFAULT_COLOR_KEY,
        frequency: HabitFrequency = HabitFrequency.Daily,
        backfillPercent: Int? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            val backfillStart = backfillPercent
                ?.let { HabitBackfill.earliestCreation(current) }
                ?.coerceAtMost(todayEpochDay)
            val backfilled = if (backfillPercent == null || backfillStart == null) emptySet()
            else HabitBackfill.generate(
                startEpochDay = backfillStart,
                endExclusiveEpochDay = todayEpochDay,
                percent = backfillPercent,
                frequency = frequency,
            )
            current + Habit(
                name = trimmed,
                description = description.trim(),
                iconKey = iconKey,
                colorKey = colorKey,
                frequency = frequency,
                createdAtEpochDay = backfillStart ?: todayEpochDay,
                completedDays = backfilled,
                trackedSinceEpochDay = if (backfillStart == null) null else todayEpochDay,
            )
        }
    }

    /**
     * Extends [habitId]'s history backwards to the day the user's oldest
     * habit was created, filling the days that opens up at roughly [percent]
     * success.
     *
     * Purely additive: the habit's own recorded days are never touched, only
     * the stretch of time in front of its old creation date is filled in.
     * No-op when the habit is already the oldest one (nothing to reach back
     * to) or when it can't be found.
     */
    suspend fun backfillHistory(habitId: String, percent: Int) {
        update { current ->
            val start = HabitBackfill.earliestCreation(current) ?: return@update current
            current.map { h ->
                if (h.id != habitId || start >= h.createdAtEpochDay) h
                else h.copy(
                    createdAtEpochDay = start,
                    // Keep the earliest real day if it was already backfilled once.
                    trackedSinceEpochDay = h.realStartEpochDay,
                    completedDays = h.completedDays + HabitBackfill.generate(
                        startEpochDay = start,
                        endExclusiveEpochDay = h.createdAtEpochDay,
                        percent = percent,
                        frequency = h.frequency,
                    ),
                )
            }
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

    /** Pause / unpause a habit. Pausing freezes streaks; see [Habit.withPaused]. */
    suspend fun setPaused(habitId: String, paused: Boolean, todayEpochDay: Long) {
        update { current ->
            current.map { h -> if (h.id != habitId) h else h.withPaused(paused, todayEpochDay) }
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

    /** Archive / restore a habit, recording when; see [Habit.withArchived]. */
    suspend fun setArchived(habitId: String, archived: Boolean, todayEpochDay: Long) {
        update { current ->
            current.map { h -> if (h.id == habitId) h.withArchived(archived, todayEpochDay) else h }
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
        // Backups made before inverse habits were removed still carry the
        // flag, so they get the same history conversion as stored data.
        val invertHistory = SettingsRepository(context).legacyInverseHabitsEnabled()
        val parsed = runCatching { decodeMigratingInverse(rawJson, invertHistory) }.getOrNull()
            ?: return null
        update { parsed }
        return parsed.size
    }

    /**
     * One-off upgrade for habits saved while "inverse" (bad-habit breaking)
     * habits existed. Those stored the days the bad habit *happened* in
     * [Habit.completedDays], so once the flag is gone they'd read as the
     * days the habit was done — the exact opposite. Rewriting them to the
     * clean days keeps their All Time grid meaning the same thing, and from
     * then on they're ordinary habits ticked off on days they're kept.
     *
     * If the user had the "Allow inverse habits" toggle off, the app was
     * already treating these as ordinary habits, so their history is kept
     * as-is and only the flag is dropped.
     *
     * Cheap no-op once migrated: the flag is never written again, so the
     * marker check fails on every later call.
     */
    private suspend fun migrateLegacyInverseHabits() {
        val raw = context.habitsDataStore.data.first()[KEY_HABITS_JSON] ?: return
        if (LEGACY_INVERSE_MARKER !in raw) return
        val invertHistory = SettingsRepository(context).legacyInverseHabitsEnabled()
        context.habitsDataStore.edit { prefs ->
            val current = prefs[KEY_HABITS_JSON] ?: return@edit
            val migrated = runCatching { decodeMigratingInverse(current, invertHistory) }
                .getOrNull() ?: return@edit
            prefs[KEY_HABITS_JSON] = json.encodeToString(listSerializer, migrated)
        }
    }

    /**
     * Decodes [raw], converting any habit still flagged `"inverse": true`.
     * See [migrateLegacyInverseHabits]. Throws if [raw] isn't a habit list.
     */
    private fun decodeMigratingInverse(raw: String, invertHistory: Boolean): List<Habit> {
        val habits = json.decodeFromString(listSerializer, raw)
        return if (invertHistory) invertLegacyInverseHistory(raw, habits, LocalDate.now().toEpochDay())
        else habits
    }

    private suspend fun update(block: (List<Habit>) -> List<Habit>) {
        // Migrate first so a write (e.g. a widget tap) before the app has
        // ever read the list can't silently drop the legacy flag.
        migrateLegacyInverseHabits()
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

        /** How a still-flagged legacy inverse habit appears in the stored (compact) JSON. */
        const val LEGACY_INVERSE_MARKER = "\"inverse\":true"
    }
}

/**
 * Rewrites the history of every habit flagged `"inverse": true` in [raw]
 * (the JSON [habits] was decoded from, which still carries the flag) from
 * the days the bad habit happened to the clean days. Pure so it can be
 * reasoned about apart from DataStore; see
 * [HabitRepository.migrateLegacyInverseHabits] for why.
 */
internal fun invertLegacyInverseHistory(raw: String, habits: List<Habit>, today: Long): List<Habit> {
    val inverseIds = Json.parseToJsonElement(raw).jsonArray.mapNotNullTo(mutableSetOf()) { el ->
        val obj = el.jsonObject
        if (obj["inverse"]?.jsonPrimitive?.booleanOrNull == true) {
            obj["id"]?.jsonPrimitive?.contentOrNull
        } else null
    }
    if (inverseIds.isEmpty()) return habits
    return habits.map { h ->
        if (h.id !in inverseIds) return@map h
        // Clean days are the tracked days that weren't a slip, a skip or
        // paused. Today is left open: it isn't over, so it's the user's to
        // tick off like any other habit.
        val clean = (h.createdAtEpochDay until today).filterTo(mutableSetOf()) { day ->
            day !in h.completedDays && day !in h.skippedDays && !h.isPausedOn(day)
        }
        h.copy(completedDays = clean)
    }
}
