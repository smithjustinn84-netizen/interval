package dev.justinsmith.interval.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm per reminder, which fires [PlanReminderReceiver] to post the notification. The alarms are
 * inexact (`setAndAllowWhileIdle`, so no exact-alarm permission to ask for): the system may hold one back to
 * batch it, up to an hour by its own window though minutes as a rule, and in Doze delivers it all the same,
 * which a reminder can bear. Exact delivery would take `SCHEDULE_EXACT_ALARM`, a special permission the user
 * grants on a settings page. Each reminder is worded here, as it is scheduled, since reading the app's strings
 * suspends, and the words ride in the alarm's intent.
 */
internal class AndroidPlanReminders(private val context: Context) : PlanReminders {
    override suspend fun schedule(reminders: List<PlanReminder>) {
        val alarms = reminders.map { reminder ->
            val words = reminderWords(reminder)
            PlanAlarm(reminder.epochDay, reminder.minuteOfDay, words.title, words.body)
        }
        PlanAlarms.replace(context, alarms)
    }
}

/** A reminder as its alarm carries it: when, and the notification's words. */
internal class PlanAlarm(val epochDay: Long, val minuteOfDay: Int, val title: String, val text: String)

/**
 * The alarms behind the reminders, and the record of them (a SharedPreferences file) by which each one is
 * cancelled exactly when the set changes and the whole set is armed again after a reboot or a change of
 * clock or time zone, which drop or move them.
 */
internal object PlanAlarms {
    private val log = Logger.withTag("PlanAlarms")

    /** Cancels every alarm set before and sets one per [alarms], recording them for [rearm]. */
    fun replace(context: Context, alarms: List<PlanAlarm>) {
        val prefs = context.prefs()
        val manager = context.getSystemService(AlarmManager::class.java)
        for (day in prefs.days()) manager?.cancel(context.showIntent(day))
        prefs.edit().apply {
            clear()
            putStringSet(KEY_DAYS, alarms.map { it.epochDay.toString() }.toSet())
            for (alarm in alarms) {
                putInt(KEY_MINUTE + alarm.epochDay, alarm.minuteOfDay)
                putString(KEY_TITLE + alarm.epochDay, alarm.title)
                putString(KEY_TEXT + alarm.epochDay, alarm.text)
            }
        }.apply()
        arm(context, manager, alarms)
    }

    /** Sets the recorded alarms again, at their day and time on the clock as it now stands. */
    fun rearm(context: Context) {
        arm(context, context.getSystemService(AlarmManager::class.java), stored(context))
    }

    private fun arm(context: Context, manager: AlarmManager?, alarms: List<PlanAlarm>) {
        if (manager == null) return
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            val at = alarm.triggerMillis()
            // Its moment has gone: a reminder now would be a stale one.
            if (at <= now) continue
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, context.showIntent(alarm)) }
                .onFailure { log.w(it) { "Couldn't set the reminder for day ${alarm.epochDay}" } }
        }
    }

    private fun stored(context: Context): List<PlanAlarm> {
        val prefs = context.prefs()
        return prefs.days().mapNotNull { day ->
            val title = prefs.getString(KEY_TITLE + day, null) ?: return@mapNotNull null
            val text = prefs.getString(KEY_TEXT + day, null) ?: return@mapNotNull null
            PlanAlarm(day, prefs.getInt(KEY_MINUTE + day, 0), title, text)
        }
    }

    // The local wall-clock time of that day, so a time zone change moves the alarm with the user, and a
    // time that daylight saving skips goes forward to the next that exists.
    private fun PlanAlarm.triggerMillis(): Long = LocalDate.ofEpochDay(epochDay)
        .atTime(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun Context.prefs(): SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.days(): List<Long> =
        getStringSet(KEY_DAYS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }

    private fun Context.showIntent(alarm: PlanAlarm): PendingIntent = showIntent(alarm.epochDay) {
        putExtra(EXTRA_DAY, alarm.epochDay)
        putExtra(EXTRA_TITLE, alarm.title)
        putExtra(EXTRA_TEXT, alarm.text)
    }

    /**
     * The one pending intent for [day]'s alarm, told apart by the day as its request code and matched by the
     * action alone, extras aside: so the same day's alarm is replaced rather than doubled, and a cancel finds it
     * without its words. Epoch days are tens of thousands, well within an Int.
     */
    private fun Context.showIntent(day: Long, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(this, PlanReminderReceiver::class.java).setAction(ACTION_SHOW).apply(extras)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(this, day.toInt(), intent, flags)
    }

    internal const val ACTION_SHOW = "dev.justinsmith.interval.SHOW_REMINDER"
    internal const val EXTRA_DAY = "day"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_TEXT = "text"

    private const val PREFS_NAME = "plan_reminders"
    private const val KEY_DAYS = "days"
    private const val KEY_MINUTE = "minute_"
    private const val KEY_TITLE = "title_"
    private const val KEY_TEXT = "text_"
}
