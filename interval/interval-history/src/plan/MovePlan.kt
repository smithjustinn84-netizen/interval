package dev.justinsmith.interval.plan

import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.weekdayOf

/** A day a plan can be moved to: its [workouts] planned and its [sport], planned or logged, so the sheet can say. */
data class MoveDay(val epochDay: Long, val weekday: Int, val workouts: List<PlanWorkout>, val sport: Sport?) {
    /** Nothing planned and no sport: the day a move goes to first. */
    val free: Boolean get() = workouts.isEmpty() && sport == null
}

/**
 * The move sheet for [plan] off [fromDay]: the [days] it can go to and the one [suggested], the first free.
 */
data class MoveSheet(val plan: PlannedWorkout, val fromDay: Long, val days: List<MoveDay>, val suggested: Long?)

/**
 * The [MOVE_DAYS] days [plan] can be moved to off [fromDay]: from the day after it, or from today for a day gone by,
 * and never the day it was first moved from, which would show it there twice. Moves go forward, so a missed day is
 * caught up and a day to come is put off, never brought nearer. The suggestion is the first free day, else the
 * first.
 */
fun moveSheet(plan: PlannedWorkout, fromDay: Long, today: Long, log: TrainingLog): MoveSheet {
    val first = maxOf(today, fromDay + 1)
    val days = (first until first + MOVE_DAYS).filter { it != plan.movedFromDay }.map { epochDay ->
        val day = log.day(epochDay)
        MoveDay(
            epochDay,
            weekdayOf(epochDay),
            day.plans.map { it.plan.workout }.filterNot { it is PlanWorkout.SportDay },
            day.sport,
        )
    }
    return MoveSheet(plan, fromDay, days, (days.firstOrNull { it.free } ?: days.firstOrNull())?.epochDay)
}

/** A week of days to move a plan to. */
const val MOVE_DAYS = 7
