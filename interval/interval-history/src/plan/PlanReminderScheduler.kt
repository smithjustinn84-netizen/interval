package dev.justinsmith.interval.plan

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.asLiftWorkouts
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.PlanReminder
import dev.justinsmith.interval.platform.PlanReminderSource
import dev.justinsmith.interval.platform.PlanReminders
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.LiftSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the platform's reminders ([PlanReminders]) in step with the plan for as long as [scope] runs: each change
 * of the plan, the history (Lift's sessions too, which do its plans) or the reminder setting, and each midnight
 * ([WallClock.days]), hands the platform the reminders the plan now calls for ([plannedReminders]), the whole set
 * at once, so the platform replaces rather than adds, and the same set twice over (a theme change, say) is not sent
 * again. Nothing is sent until both sides have read what is stored, which spares the platform a cancel from the
 * defaults. With the reminder off, the set is empty, which cancels whatever was pending. A set the platform fails
 * to take is logged and skipped; the next change sends the whole set again.
 *
 * It is also the [PlanReminderSource] a reminder's alarm reads as it shows, with no screen open: the day's
 * reminder as the plan now stands, and the whole set worked out again from now ([reschedule]), which moves the two
 * weeks on even when the app is never opened.
 */
class PlanReminderScheduler(
    private val repository: IntervalRepository,
    private val lift: LiftActivity,
    private val reminders: PlanReminders,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    scope: CoroutineScope,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : PlanReminderSource {
    private val log = Logger.withTag("PlanReminderScheduler")

    init {
        scope.launch {
            awaitReady()
            combine(
                repository.planned,
                repository.history,
                lift.sessions,
                repository.appSettings,
                clock.days(timestamps, dayCheckMillis),
            ) { planned, history, sessions, app, today ->
                remindersFrom(log(planned, history, sessions, today), app, today)
            }.distinctUntilChanged().collect { scheduleLogged(it) }
        }
    }

    override suspend fun reminderOn(epochDay: Long): PlanReminder? {
        awaitReady()
        val minute = repository.appSettings.value.reminderMinuteOfDay ?: return null
        return reminderOn(logNow(), minute, epochDay)
    }

    // Straight to the platform, in the caller's coroutine, past the change-only filter above: an alarm's receiver
    // waits on it, and a set the same as before still needs setting again after the clock moves.
    override suspend fun reschedule() {
        awaitReady()
        scheduleLogged(remindersFrom(logNow(), repository.appSettings.value, today()))
    }

    private suspend fun awaitReady() {
        repository.ready.first { it }
        lift.ready.first { it }
    }

    private fun remindersFrom(log: TrainingLog, app: AppSettings, today: Long): List<PlanReminder> =
        app.reminderMinuteOfDay?.let { minute ->
            plannedReminders(log, minute, today, timestamps.minuteOfDay(clock.nowMillis()))
        }.orEmpty()

    private fun logNow(): TrainingLog =
        log(repository.planned.value, repository.history.value, lift.sessions.value, today())

    private fun log(
        planned: List<PlannedWorkout>,
        history: List<HistoryEntry>,
        sessions: List<LiftSession>,
        today: Long,
    ) = TrainingLog(history, planned, today, sessions.asLiftWorkouts(), timestamps::dayNumber)

    private fun today(): Long = timestamps.dayNumber(clock.nowMillis())

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
