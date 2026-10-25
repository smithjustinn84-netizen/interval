package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutState

/**
 * A workout under way, or just over, as it last stood: kept so that a process the system kills mid-workout
 * picks the workout up where it had got to, or shows the summary of what ran, rather than starting again
 * from Get ready.
 *
 * Written at each change of course (start, pause, resume, skip, back, added time, an interval's end, the
 * end), not at each tick: from [atEpochMillis] and [running], a restore works out how far a running workout
 * has got since. [id] is the one its route carries, so a snapshot left behind (the app swiped away with
 * nothing to restore) never catches a later workout.
 */
data class WorkoutProgress(
    val id: Long,
    val state: WorkoutState,
    val running: Boolean,
    /** Ended before its last round, as [dev.justinsmith.interval.workout.WorkoutUiState.ended]. */
    val ended: Boolean,
    /** Time the timer has run, pauses excluded, in milliseconds. */
    val activeMillis: Long,
    /** Of [activeMillis], the time spent in work intervals. */
    val workMillis: Long,
    /** Where the week stood with this workout in it, once it was recorded; null while it runs. */
    val week: WeekStanding?,
    /** The wall-clock moment this stood, in epoch milliseconds. */
    val atEpochMillis: Long,
)
