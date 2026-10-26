package dev.justinsmith.interval.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.notification_channel_reminders
import dev.justinsmith.interval.platform.resources.notification_channel_reminders_description
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/**
 * Where a reminder's alarm lands: it posts the notification whose words the alarm carries, on a channel of
 * its own ("Planned workouts") that the user can quieten apart from the workout's. Also told of a reboot and
 * of the clock or time zone changing, after which it sets the recorded alarms again ([PlanAlarms.rearm]).
 * Declared in the app's manifest, by this name.
 */
class PlanReminderReceiver : BroadcastReceiver() {
    private val log = Logger.withTag("PlanReminderReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlanAlarms.ACTION_SHOW -> show(context, intent)

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> PlanAlarms.rearm(context)
        }
    }

    private fun show(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Refused, or never granted: the alarm fires into nothing, and the plan's page asks again.
        if (!manager.areNotificationsEnabled()) return
        // The channel must exist before the first notification. Reading two short strings is the one wait.
        runBlocking { createChannel(manager) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.notificationIcon())
            .setContentTitle(intent.getStringExtra(PlanAlarms.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(PlanAlarms.EXTRA_TEXT))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(context.openAppIntent(REQUEST_OPEN))
            .build()
        val id = intent.getLongExtra(PlanAlarms.EXTRA_DAY, 0L).toInt()
        runCatching { manager.notify(id, notification) }.onFailure { log.w(it) { "Couldn't show the reminder" } }
    }

    private suspend fun createChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(Res.string.notification_channel_reminders_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "plan_reminders"
        const val REQUEST_OPEN = 0
    }
}
