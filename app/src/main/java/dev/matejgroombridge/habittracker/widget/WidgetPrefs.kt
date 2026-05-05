package dev.matejgroombridge.habittracker.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Per-widget configuration. Each Android-assigned widget instance ID gets its
 * own list of habit IDs to show. Stored as a comma-separated string in a
 * single Preferences DataStore keyed by widget ID — overkill for the data
 * volume but consistent with how the rest of the app persists state.
 */
private val Context.widgetPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "habit_widget_prefs")

object WidgetPrefs {

    /** Up to 6 habits per widget (2 columns × 3 rows). */
    const val MAX_HABITS_PER_WIDGET = 6

    private fun key(widgetId: Int) = stringPreferencesKey("widget_$widgetId")

    /** Reactive flow of the chosen habit IDs for [widgetId]. */
    fun selectionFlow(context: Context, widgetId: Int): Flow<List<String>> =
        context.widgetPrefsDataStore.data.map { decode(it[key(widgetId)]) }

    /** One-shot read of [widgetId]'s selection. */
    suspend fun getSelection(context: Context, widgetId: Int): List<String> =
        decode(context.widgetPrefsDataStore.data.first()[key(widgetId)])

    /** Persist [habitIds] (truncated to [MAX_HABITS_PER_WIDGET]). */
    suspend fun setSelection(context: Context, widgetId: Int, habitIds: List<String>) {
        context.widgetPrefsDataStore.edit {
            it[key(widgetId)] = encode(habitIds.take(MAX_HABITS_PER_WIDGET))
        }
    }

    /** Forget a widget's config (called when the widget is deleted). */
    suspend fun clear(context: Context, widgetId: Int) {
        context.widgetPrefsDataStore.edit { it.remove(key(widgetId)) }
    }

    private fun encode(ids: List<String>): String = ids.joinToString(separator = "|")
    private fun decode(raw: String?): List<String> =
        raw?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
}
