package dev.justinsmith.interval.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The widget's receiver, declared in the manifest: the system's updates (placed, resized, a reboot) go to
 * [WeekWidget] through Glance, and each keeps the midnight alarm ahead; the alarm's own broadcast (an explicit
 * intent of the app's, never another app's) draws the widget again and sets the next.
 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
    private val log = Logger.withTag("WeekWidgetReceiver")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetDayAlarm.arm(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetDayAlarm.arm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetDayAlarm.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != WidgetDayAlarm.ACTION) return
        val result = goAsync()
        scope.launch {
            runCatching { glanceAppWidget.updateAll(context) }
                .onFailure { log.w(it) { "Couldn't draw the widget for the new day" } }
            WidgetDayAlarm.arm(context)
            result.finish()
        }
    }
}
