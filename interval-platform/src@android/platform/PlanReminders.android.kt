package dev.justinsmith.interval.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
 * The alarms behind the reminders, and the record of them (a DataStore of its own, apart from the settings
 * one: the receiver reads it after a reboot with no Koin container built) by which each one is cancelled
 * exactly when the set changes and the whole set is armed again after a reboot or a change of clock or time
 * zone, which drop or move them.
 */
internal object PlanAlarms {
    private val log = Logger.withTag("PlanAlarms")

    @Volatile
    private var store: DataStore<Preferences>? = null

    /**
     * Held across each replace and re-arm, record and alarms together: otherwise a re-arm reading the record
     * just before a replace rewrites it could set the old alarms after the new ones, untracked and never
     * cancelled.
     */
    private val changing = Mutex()

    /** Cancels every alarm set before and sets one per [alarms], recording them for [rearm]. */
    suspend fun replace(context: Context, alarms: List<PlanAlarm>) = changing.withLock {
        val manager = context.getSystemService(AlarmManager::class.java)
        context.store().updateData { record ->
            for (alarm in record.alarms()) manager?.cancel(context.showIntent(alarm.epochDay))
            alarms.asRecord()
        }
        arm(context, manager, alarms)
    }

    /** Sets the recorded alarms again, at their day and time on the clock as it now stands. */
    suspend fun rearm(context: Context) = changing.withLock {
        val alarms = context.store().data.first().alarms()
        arm(context, context.getSystemService(AlarmManager::class.java), alarms)
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

    /**
     * The process's one DataStore for the record: DataStore allows a single active instance per file, and
     * both the app (scheduling) and the receiver (re-arming) reach it.
     */
    private fun Context.store(): DataStore<Preferences> = store ?: synchronized(this@PlanAlarms) {
        store ?: PreferenceDataStoreFactory.create(
            // A record that can't be read costs only the re-arm after a reboot; the next schedule rewrites it.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            migrations = listOf(LegacyRecordMigration(applicationContext)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(applicationContext.filesDir, "datastore/$STORE_NAME") },
        ).also { store = it }
    }

    /**
     * Carries a record kept by an earlier build, in a SharedPreferences file, into the DataStore once and
     * deletes the file, so alarms it set are still cancelled and re-armed. The only SharedPreferences read
     * left in the app, as DataStore's own `SharedPreferencesMigration` does it (that class lives in the
     * Android-only `datastore-preferences`, which this module doesn't take). Safe to drop once no install
     * has the file.
     */
    private class LegacyRecordMigration(private val context: Context) : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) =
            File(context.dataDir, "shared_prefs/$LEGACY_NAME.xml").exists()

        override suspend fun migrate(currentData: Preferences): Preferences {
            val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
            val days = legacy.getStringSet(LEGACY_DAYS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
            return days.mapNotNull { day ->
                val title = legacy.getString(LEGACY_TITLE + day, null) ?: return@mapNotNull null
                val text = legacy.getString(LEGACY_TEXT + day, null) ?: return@mapNotNull null
                PlanAlarm(day, legacy.getInt(LEGACY_MINUTE + day, 0), title, text)
            }.asRecord()
        }

        override suspend fun cleanUp() {
            context.deleteSharedPreferences(LEGACY_NAME)
        }
    }

    // The local wall-clock time of that day, so a time zone change moves the alarm with the user, and a
    // time that daylight saving skips goes forward to the next that exists.
    private fun PlanAlarm.triggerMillis(): Long = LocalDate.ofEpochDay(epochDay)
        .atTime(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

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

    private const val STORE_NAME = "plan_reminders.preferences_pb"

    // The SharedPreferences file earlier builds kept, read once by [LegacyRecordMigration].
    private const val LEGACY_NAME = "plan_reminders"
    private const val LEGACY_DAYS = "days"
    private const val LEGACY_MINUTE = "minute_"
    private const val LEGACY_TITLE = "title_"
    private const val LEGACY_TEXT = "text_"
}

// The alarms as [PlanAlarms] records them: the set of days, and each day's time and words under keys of its own.
private val DAYS = stringSetPreferencesKey("days")

private fun minuteKey(day: Long) = intPreferencesKey("minute_$day")

private fun titleKey(day: Long) = stringPreferencesKey("title_$day")

private fun textKey(day: Long) = stringPreferencesKey("text_$day")

private fun Preferences.alarms(): List<PlanAlarm> =
    this[DAYS].orEmpty().mapNotNull { it.toLongOrNull() }.mapNotNull { day ->
        val title = this[titleKey(day)] ?: return@mapNotNull null
        val text = this[textKey(day)] ?: return@mapNotNull null
        PlanAlarm(day, this[minuteKey(day)] ?: 0, title, text)
    }

private fun List<PlanAlarm>.asRecord(): Preferences = mutablePreferencesOf().also { record ->
    record[DAYS] = map { it.epochDay.toString() }.toSet()
    for (alarm in this) {
        record[minuteKey(alarm.epochDay)] = alarm.minuteOfDay
        record[titleKey(alarm.epochDay)] = alarm.title
        record[textKey(alarm.epochDay)] = alarm.text
    }
}
