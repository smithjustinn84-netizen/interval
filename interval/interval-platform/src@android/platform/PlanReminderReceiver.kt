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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Where a reminder's alarm lands. It words the day's reminder from the plan as it now stands ([PlanReminderSource],
 * in the app's language of the moment) and posts it on a channel of its own ("Planned workouts") that the user can
 * quieten apart from the workout's, or posts nothing if the day's workouts were done or taken off meanwhile. Then it
 * has the reminders worked out again from now, which moves them on to two weeks past this one: so they keep coming
 * to a user who never opens the app. Also told of a reboot and of the clock or time zone changing, after which it
 * does the same, setting every alarm afresh. Declared in the app's manifest, by this name; it reaches the process's
 * container, which the application builds before any receiver runs.
 *
 * Neither job runs on the main thread, where a receiver has seconds before the system gives up on it: the plan and
 * the record are read from disk and the words suspend, and at boot the system is at its slowest to answer. Each
 * broadcast is held open ([goAsync]) and finished as its work ends.
 */
class PlanReminderReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val log = Logger.withTag("PlanReminderReceiver")
    private val reminders: PlanReminderSource by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlanAlarms.ACTION_SHOW -> {
                val app = context.applicationContext
                val day = intent.getLongExtra(PlanAlarms.EXTRA_DAY, NO_DAY)
                offMain("show the reminder") {
                    // Moved on whatever came of showing it.
                    try {
                        show(app, day)
                    } finally {
                        reminders.reschedule()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> offMain("set the reminders again") { reminders.reschedule() }
        }
    }

    private suspend fun show(app: Context, day: Long) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        // Refused, or never granted: the alarm fires into nothing, and the plan's page asks again.
        if (!manager.areNotificationsEnabled()) return
        val reminder = reminders.reminderOn(day) ?: return
        val words = reminderWords(reminder)
        // The channel must exist before the first notification.
        createChannel(manager)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(app.notificationIcon())
            .setContentTitle(words.title)
            .setContentText(words.body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(app.openAppIntent(REQUEST_OPEN))
            .build()
        manager.notify(day.toInt(), notification)
    }

    /** Holds the broadcast open while [work] runs off the main thread, and finishes it however [work] ends. */
    private fun offMain(what: String, work: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("detekt.TooGenericExceptionCaught") e: Exception) {
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

        /** No day: an alarm without one finds no reminder. */
        const val NO_DAY = Long.MIN_VALUE

        /** Every broadcast's work, off the main thread; a receiver's own instance lives for one call. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
