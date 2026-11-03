package dev.justinsmith.interval.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm at the next local midnight, which has [WeekWidgetReceiver] draw the widget again: the day turns,
 * and on Monday the week with it, with the app never opened. Inexact and without waking the device (no one
 * looks at a home screen with the screen off; it lands when the device next wakes), so no exact-alarm
 * permission, as the plan's reminders. Armed again on every update, so there is always one ahead while a
 * widget is placed; cancelled as the last is removed.
 */
internal object WidgetDayAlarm {
    const val ACTION = "dev.justinsmith.interval.WIDGET_DAY_CHANGED"
    private const val REQUEST_CODE = 1
    private val log = Logger.withTag("WidgetDayAlarm")

    fun arm(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC, midnight, pending(context)) }
            .onFailure { log.w(it) { "Couldn't set the widget's midnight alarm" } }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WeekWidgetReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
