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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Where a reminder's alarm lands: it posts the notification whose words the alarm carries, on a channel of
 * its own ("Planned workouts") that the user can quieten apart from the workout's. Also told of a reboot and
 * of the clock or time zone changing, after which it sets the recorded alarms again ([PlanAlarms.rearm]).
 * Declared in the app's manifest, by this name.
 *
 * Neither job runs on the main thread, where a receiver has seconds before the system gives up on it: the
 * record and the channel's words are read from disk (the words suspend), and at boot, when the alarms are set
 * again, the system is at its slowest to answer. Each broadcast is held open ([goAsync]) and finished as its
 * work ends.
 */
class PlanReminderReceiver : BroadcastReceiver() {
    private val log = Logger.withTag("PlanReminderReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlanAlarms.ACTION_SHOW -> show(context, intent)

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> offMain("set the reminders again") { PlanAlarms.rearm(context.applicationContext) }
        }
    }

    private fun show(context: Context, intent: Intent) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        // Refused, or never granted: the alarm fires into nothing, and the plan's page asks again.
        if (!manager.areNotificationsEnabled()) return
        val title = intent.getStringExtra(PlanAlarms.EXTRA_TITLE)
        val text = intent.getStringExtra(PlanAlarms.EXTRA_TEXT)
        val id = intent.getLongExtra(PlanAlarms.EXTRA_DAY, 0L).toInt()
        offMain("show the reminder") {
            // The channel must exist before the first notification.
            createChannel(manager)
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(app.notificationIcon())
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(app.openAppIntent(REQUEST_OPEN))
                .build()
            manager.notify(id, notification)
        }
    }

    /** Holds the broadcast open while [work] runs off the main thread, and finishes it however [work] ends. */
    private fun offMain(what: String, work: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.w(e) { "Couldn't $what" }
            } finally {
                pending.finish()
            }
        }
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

        /** Every broadcast's work, off the main thread; a receiver's own instance lives for one call. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
