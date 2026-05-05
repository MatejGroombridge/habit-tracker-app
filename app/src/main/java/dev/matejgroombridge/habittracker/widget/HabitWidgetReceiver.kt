package dev.matejgroombridge.habittracker.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boilerplate AppWidget receiver. The actual UI is rendered by [HabitWidget];
 * this class wires Glance into the AppWidgetManager lifecycle.
 *
 * Also forwards the "REFRESH" custom action — fired by other parts of the
 * app whenever habit data changes — to a re-render of every widget instance.
 */
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = HabitWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            CoroutineScope(Dispatchers.IO).launch {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(HabitWidget::class.java)
                for (gid in ids) glanceAppWidget.update(context, gid)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Forget the per-widget habit selection so we don't leak storage.
        CoroutineScope(Dispatchers.IO).launch {
            for (id in appWidgetIds) WidgetPrefs.clear(context, id)
        }
    }

    companion object {
        /**
         * Fire this from anywhere in the app to redraw every widget. Used
         * by [HabitRepository] after each save so the widgets stay in sync
         * with the in-app UI.
         */
        const val ACTION_REFRESH = "dev.matejgroombridge.habittracker.WIDGET_REFRESH"

        fun broadcastRefresh(context: Context) {
            val intent = Intent(context, HabitWidgetReceiver::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
