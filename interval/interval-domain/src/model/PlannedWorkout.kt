package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.weekdayOf

/** When a planned workout falls: every week on one weekday, or once on one day. */
sealed interface PlanSchedule {
    /** Every week on [weekday], 1 for Monday through 7 for Sunday. */
    data class Weekly(val weekday: Int) : PlanSchedule

    /** Once, on [epochDay] (see `CalendarDate`). */
    data class Once(val epochDay: Long) : PlanSchedule
}

/** What a plan asks for: an interval workout of one shape, a session on Lift, or a day of a sport. */
sealed interface PlanWorkout {
    /**
     * [name] in [config]'s shape, its own copy of the preset's, so editing or deleting that preset later leaves
     * the plan as it was made; [presetId] names the user's own preset it came from, and is null for a built-in
     * or the Timer's settings.
     */
    data class Interval(val name: String, val config: IntervalConfig, val presetId: Long? = null) : PlanWorkout

    /** A session on Lift, which any session finished on its day does. */
    sealed interface Lift : PlanWorkout {
        /** The next day of the program followed, whichever it is when the day comes; a free session without one. */
        data object ProgramDay : Lift

        /** Any session at all. */
        data object AnySession : Lift
    }

    /**
     * A day of [sport], trained away from the app: nothing here starts it, and logging it that day does it. One
     * gone by unlogged was not missed, as far as the app can tell.
     */
    data class SportDay(val sport: Sport) : PlanWorkout
}

/**
 * A [workout] the user means to do on its [schedule]. A weekly plan counts from the day it was made,
 * [createdAtEpochMillis]: the weeks before it hold nothing it could have missed.
 */
data class PlannedWorkout(
    val id: Long,
    val workout: PlanWorkout,
    val schedule: PlanSchedule,
    val createdAtEpochMillis: Long = 0,
) {
    /** Whether its schedule falls on [epochDay]. When it was made is the caller's to weigh. */
    fun fallsOn(epochDay: Long): Boolean = when (schedule) {
        is PlanSchedule.Weekly -> schedule.weekday == weekdayOf(epochDay)
        is PlanSchedule.Once -> schedule.epochDay == epochDay
    }
}

/** This preset as a plan asks for it. */
fun Preset.asPlan(): PlanWorkout.Interval = PlanWorkout.Interval(name, config, id)
