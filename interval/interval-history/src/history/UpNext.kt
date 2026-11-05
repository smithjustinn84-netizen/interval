package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

// What History and Today both build from the log: what is up next, Lift's side of it, and a day of the week strip.

/** Lift's side: its finished sessions, the followed program's next day, and whether one is in progress. */
internal data class Lifting(val sessions: List<LiftSession>, val programDay: ProgramDay?, val inProgress: Boolean)

/**
 * Lift's side as it changes. Distinct, so a set logged on Lift (which changes `active`) rebuilds nothing: only a
 * session finished, the program's day or whether one is under way at all count.
 */
internal fun LiftActivity.lifting(): Flow<Lifting> = combine(sessions, programDay, active) { sessions, day, active ->
    Lifting(sessions, day, active != null)
}.distinctUntilChanged()

/**
 * The plans still to do from [today], by day. A plan of the program's next day takes the day the program will be
 * at by then: the first of them the next day, each after it the day after. With no plan of a session on Lift at
 * all, the program's next day falls today, unless a day of it was finished today already, and leads the plans of
 * its day.
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
        UpNextItem.Lift(epochDay, heading(epochDay, today), day, resume = lifting.inProgress)
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
                UpNextItem.Lift(next.epochDay, day, ofProgram, resume = resume, planId = next.plan.id)
                    .also { resume = false }
            }
        }
    }
    return (listOfNotNull(unplanned) + plans).sortedBy { it.epochDay }
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
)
