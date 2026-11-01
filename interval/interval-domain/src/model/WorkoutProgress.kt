package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.Phase
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
    /** When the workout began, in epoch milliseconds: the first moment of its course. */
    val startedAtEpochMillis: Long = atEpochMillis,
    /** Its course so far, each stretch closed at a change; the one under way runs from the last one's end. */
    val spans: List<CourseSpan> = emptyList(),
    /** The uid it was recorded in History under, once it was; what the summary's effort and note update. */
    val entryUid: String? = null,
    /** How hard it felt, as said on the summary so far. */
    val effort: PerceivedEffort? = null,
    /** The summary's note as typed so far, kept as it stands. */
    val note: String = "",
)

/**
 * One stretch of a workout's course on the wall clock: [phase] from [startEpochMillis] to
 * [endEpochMillis], or a pause when [phase] is null. The health store takes a finished workout's spans as
 * its segments.
 */
data class CourseSpan(val phase: Phase?, val startEpochMillis: Long, val endEpochMillis: Long)
