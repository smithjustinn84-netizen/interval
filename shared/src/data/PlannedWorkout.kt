package dev.justinsmith.interval.data

import dev.justinsmith.interval.timer.IntervalConfig

/** When a planned workout falls: every week on one weekday, or once on one day. */
sealed interface PlanSchedule {
    /** Every week on [weekday], 1 for Monday through 7 for Sunday. */
    data class Weekly(val weekday: Int) : PlanSchedule

    /** Once, on [epochDay] (see `CalendarDate`). */
    data class Once(val epochDay: Long) : PlanSchedule
}

/**
 * A workout the user means to do. It keeps its own copy of the preset's [name] and shape, so editing or
 * deleting that preset later leaves the plan as it was made; [presetId] names the user's own preset it
 * came from, and is null for a built-in or the Timer's settings.
 */
data class PlannedWorkout(
    val id: Long,
    val name: String,
    val config: IntervalConfig,
    val schedule: PlanSchedule,
    val presetId: Long? = null,
) {
    /** Whether it falls on [epochDay], which is [weekday] (1 for Monday) of its week. */
    fun fallsOn(epochDay: Long, weekday: Int): Boolean = when (schedule) {
        is PlanSchedule.Weekly -> schedule.weekday == weekday
        is PlanSchedule.Once -> schedule.epochDay == epochDay
    }
}
