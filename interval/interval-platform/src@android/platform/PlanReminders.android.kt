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
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * grants on a settings page. An alarm carries its day alone: the receiver words the notification as it shows,
 * from the plan as it then stands ([PlanReminderSource]).
 */
internal class AndroidPlanReminders(private val alarms: PlanAlarms) : PlanReminders {
    override suspend fun schedule(reminders: List<PlanReminder>) {
        alarms.replace(reminders.map { PlanAlarm(it.epochDay, it.minuteOfDay) })
    }
}

/** A reminder as its alarm is set: the day, and the minute after that day's local midnight. */
internal class PlanAlarm(val epochDay: Long, val minuteOfDay: Int)

/**
 * The alarms behind the reminders, one of these per process (the container's), and the record of the days that
 * have one ([store], a DataStore of its own), by which each is cancelled exactly when the set changes. A reboot
 * drops them and a change of clock or time zone leaves them at the wrong moment: the receiver then has the whole
 * set worked out again ([PlanReminderSource.reschedule]), which cancels by the record and sets them afresh.
 */
internal class PlanAlarms(private val context: Context, private val store: DataStore<Preferences>) {
    private val log = Logger.withTag("PlanAlarms")

    /**
     * Held across each replace, record and alarms together: otherwise two replaces at once (a change and an alarm's
     * reschedule) could each cancel by the record before the other's alarms were set, leaving some untracked.
     */
    private val changing = Mutex()

    /** Cancels every alarm set before and sets one per [alarms], recording their days. */
    suspend fun replace(alarms: List<PlanAlarm>) = changing.withLock {
        val manager = context.getSystemService(AlarmManager::class.java)
        store.updateData { record ->
            for (day in record.days()) manager?.cancel(context.showIntent(day))
            alarms.asRecord()
        }
        arm(manager, alarms)
    }

    private fun arm(manager: AlarmManager?, alarms: List<PlanAlarm>) {
        if (manager == null) return
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            val at = alarm.triggerMillis()
            // Its moment has gone: a reminder now would be a stale one.
            if (at <= now) continue
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, context.showIntent(alarm.epochDay))
            }.onFailure { log.w(it) { "Couldn't set the reminder for day ${alarm.epochDay}" } }
        }
    }

    // The local wall-clock time of that day, so a time zone change moves the alarm with the user, and a
    // time that daylight saving skips goes forward to the next that exists.
    private fun PlanAlarm.triggerMillis(): Long = LocalDate.ofEpochDay(epochDay)
        .atTime(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    /**
     * The one pending intent for [day]'s alarm, told apart by the day as its request code and matched by the
     * action alone, extras aside: so the same day's alarm is replaced rather than doubled, and a cancel finds it,
     * one set by an earlier build too. Epoch days are tens of thousands, well within an Int.
     */
    private fun Context.showIntent(day: Long): PendingIntent {
        val intent = Intent(this, PlanReminderReceiver::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_DAY, day)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(this, day.toInt(), intent, flags)
    }

    companion object {
        // The action, the extra and the record's name and key are what earlier builds used, so the alarms they
        // set reach the receiver still, and the first replace after an update finds and cancels them.
        internal const val ACTION_SHOW = "dev.justinsmith.interval.SHOW_REMINDER"
        internal const val EXTRA_DAY = "day"
    }
}

/**
 * The record's DataStore, which the container keeps one of: DataStore allows a single active instance per file.
 * A record that can't be read costs only the cancel of what it named; the next replace rewrites it.
 */
internal fun planAlarmStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    migrations = listOf(LegacyRecordMigration(context)),
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    produceFile = { File(context.filesDir, "datastore/$STORE_NAME") },
)

/**
 * Carries a record kept by an earlier build, in a SharedPreferences file, into the DataStore once and deletes
 * the file, so alarms it set are still cancelled. The only SharedPreferences read left in the app, as DataStore's
 * own `SharedPreferencesMigration` does it (that class lives in the Android-only `datastore-preferences`, which
 * this module doesn't take). Safe to drop once no install has the file.
 */
private class LegacyRecordMigration(private val context: Context) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) =
        File(context.dataDir, "shared_prefs/$LEGACY_NAME.xml").exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
        val days = legacy.getStringSet(LEGACY_DAYS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        return days.map { PlanAlarm(it, minuteOfDay = 0) }.asRecord()
    }

    override suspend fun cleanUp() {
        context.deleteSharedPreferences(LEGACY_NAME)
    }
}

private const val STORE_NAME = "plan_reminders.preferences_pb"

// The SharedPreferences file earlier builds kept, read once by [LegacyRecordMigration].
private const val LEGACY_NAME = "plan_reminders"
private const val LEGACY_DAYS = "days"

// The alarms as [PlanAlarms] records them: the days that have one. Earlier builds kept each day's time and words
// beside it, under keys the first replace drops.
private val DAYS = stringSetPreferencesKey("days")

private fun Preferences.days(): List<Long> = this[DAYS].orEmpty().mapNotNull { it.toLongOrNull() }

private fun List<PlanAlarm>.asRecord(): Preferences = mutablePreferencesOf().also { record ->
    record[DAYS] = map { it.epochDay.toString() }.toSet()
}
