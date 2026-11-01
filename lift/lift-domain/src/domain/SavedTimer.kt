package dev.justinsmith.lift.domain

/**
 * The session's timer running, as the session in progress keeps it: enough to go on with it after the process
 * is killed, timed again from [startedAtEpochMillis] by the wall clock. One at a time, as the timers run.
 */
sealed interface SavedTimer {
    val startedAtEpochMillis: Long

    /** A rest of [seconds], as adjusted. */
    data class Rest(override val startedAtEpochMillis: Long, val seconds: Int) : SavedTimer

    /** An EMOM of a lift, its load and reps, with the minutes whose set is [done]. */
    data class Emom(
        override val startedAtEpochMillis: Long,
        val exercise: Exercise,
        val bellKg: Double,
        val reps: Int,
        val minutes: Int,
        val firstSide: Side,
        val double: Boolean,
        val band: Band?,
        val done: Set<Int>,
    ) : SavedTimer

    /** A hold of [seconds], the set it logs as the Log sheet had it, and whether it is the [firstSide]. */
    data class Hold(
        override val startedAtEpochMillis: Long,
        val exercise: Exercise,
        val bellKg: Double,
        val addedKg: Double,
        val barKg: Double,
        val side: Side,
        val double: Boolean,
        val seconds: Int,
        val band: Band,
        val firstSide: Boolean,
    ) : SavedTimer
}
