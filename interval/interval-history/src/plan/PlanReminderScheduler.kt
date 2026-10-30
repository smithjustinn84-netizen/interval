package dev.justinsmith.interval.plan

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.PlanReminder
import dev.justinsmith.interval.platform.PlanReminders
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the platform's reminders ([PlanReminders]) in step with the plan for as long as [scope] runs: each
 * change of the plan, the history or the reminder setting, and each midnight ([WallClock.days]), hands the
 * platform the reminders the plan now calls for ([plannedReminders]), the whole set at once, so the platform
 * replaces rather than adds, and the same set twice over (a theme change, say) is not sent again. Nothing is
 * sent until the repository has read what is stored, which spares the platform a cancel from the defaults.
 * With the reminder off, the set is empty, which cancels whatever was pending. A set the platform fails to
 * take is logged and skipped; the next change sends the whole set again.
 */
class PlanReminderScheduler(
    private val repository: IntervalRepository,
    private val reminders: PlanReminders,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    scope: CoroutineScope,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) {
    private val log = Logger.withTag("PlanReminderScheduler")

    init {
        scope.launch {
            repository.ready.first { it }
            combine(
                repository.planned,
                repository.history,
                repository.appSettings,
                clock.days(timestamps, dayCheckMillis),
            ) { planned, history, app, today ->
                app.reminderMinuteOfDay?.let { minute ->
                    val log = TrainingLog(history, planned, today, dayOf = timestamps::dayNumber)
                    plannedReminders(log, minute, today, timestamps.minuteOfDay(clock.nowMillis()))
                }.orEmpty()
            }.distinctUntilChanged().collect { scheduleLogged(it) }
        }
    }

    // A reminder that can't be set must not end the app, nor stop the reminders after it.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun scheduleLogged(set: List<PlanReminder>) {
        try {
            reminders.schedule(set)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't schedule the plan's reminders" }
        }
    }
}
