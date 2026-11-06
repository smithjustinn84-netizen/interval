package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.flowBefore
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.estimatedMinutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

// What History and Today both build from the log: what is up next, Lift's side of it, and a day of the week strip.

/**
 * Lift's side: its finished sessions, the followed program's next day, whether one is in progress, and the rest
 * between sets, which a day's length is estimated with.
 */
internal data class Lifting(
    val sessions: List<LiftSession>,
    val programDay: ProgramDay?,
    val inProgress: Boolean,
    val restSeconds: Int = RestClock.DEFAULT_SECONDS,
)

/** Everything done, as History reads it: the interval workouts, the sport days logged and Lift's side. */
internal data class Logged(val history: List<HistoryEntry>, val sports: List<LoggedSport>, val lifting: Lifting)

/**
 * Lift's side as it changes. Distinct, so a set logged on Lift (which changes `active`) rebuilds nothing: only a
 * session finished, the program's day or whether one is under way at all count.
 */
internal fun LiftActivity.lifting(): Flow<Lifting> =
    combine(sessions, programDay, active, restSeconds) { sessions, day, active, rest ->
        Lifting(sessions, day, active != null, rest)
    }.distinctUntilChanged()

/**
 * The plans still to do from [today], by day. A plan of the program's next day takes the day the program will be
 * at by then: the first of them the next day, each after it the day after. With no plan of a session on Lift at
 * all, the program's next day falls today, unless a day of it was finished today already, and leads the plans of
 * its day. A sport planned today and not yet logged brings the flow to do before it, until mobility is done.
 */
internal fun TimestampFormatter.upNext(
    log: TrainingLog,
    planned: List<PlannedWorkout>,
    lifting: Lifting,
    today: Long,
): List<UpNextItem> {
    val unplanned = lifting.programDay?.takeIf { planned.none { it.workout is PlanWorkout.Lift } }?.let { day ->
        val doneToday = lifting.sessions.any { session ->
            session.program == day.program.key && session.finishedAtEpochMillis?.let(::dayNumber) == today
        }
        val epochDay = if (doneToday) today + 1 else today
        UpNextItem.Lift(
            epochDay,
            heading(epochDay, today),
            day,
            resume = lifting.inProgress,
            minutes = day.takeUnless { lifting.inProgress }?.estimatedMinutes(lifting.restSeconds),
        )
    }
    var programDay = lifting.programDay
    var resume = lifting.inProgress
    val plans = log.upNext().map { next ->
        val day = heading(next.epochDay, today)
        when (val workout = next.plan.workout) {
            is PlanWorkout.Interval -> UpNextItem.Planned(
                next.plan.id,
                next.epochDay,
                day,
                workout.name,
                workout.config,
            )

            is PlanWorkout.Lift -> {
                val ofProgram = programDay?.takeIf { workout == PlanWorkout.Lift.ProgramDay }
                if (ofProgram != null) programDay = ofProgram.program.dayAfter(ofProgram.number)
                // The session in progress is part done: only a day still to start says how long it takes.
                val minutes = ofProgram?.takeUnless { resume }?.estimatedMinutes(lifting.restSeconds)
                UpNextItem.Lift(next.epochDay, day, ofProgram, resume, next.plan.id, minutes).also { resume = false }
            }

            is PlanWorkout.SportDay -> UpNextItem.SportDay(next.plan.id, next.epochDay, day, workout.sport)
        }
    }
    // A day with a sport still to log, and no mobility done on it yet, is offered the flow to do before it.
    val sport = plans.filterIsInstance<UpNextItem.SportDay>().firstOrNull { it.epochDay == today }?.sport
    val suggested = sport?.takeIf { log.day(today).mobility == 0 }?.let {
        UpNextItem.Flow(today, heading(today, today), flowBefore(it), it)
    }
    return (listOfNotNull(unplanned, suggested) + plans).sortedBy { it.epochDay }
}

/** [day] as the week strip or the calendar draws it. */
internal fun TimestampFormatter.dayCell(day: TrainingDay, inMonth: Boolean = true) = DayCell(
    epochDay = day.epochDay,
    weekday = day.weekday,
    dayOfMonth = dateOfEpochDay(day.epochDay).day,
    state = day.state,
    seconds = day.seconds,
    today = day.isToday,
    inMonth = inMonth,
    date = dateOfDay(day.epochDay),
    liftSeconds = day.liftWorkouts.sumOf { it.elapsedSeconds },
    sport = day.sport,
    sportLogged = day.sports.isNotEmpty(),
    sportPassed = day.sports.isEmpty() && day.plans.any { it.state == PlanState.Passed },
)
