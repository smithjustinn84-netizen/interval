package dev.justinsmith.interval.model

/** A finished workout of either kind, as History lists them side by side. */
sealed interface LoggedWorkout {
    val finishedAtEpochMillis: Long

    data class Interval(val entry: HistoryEntry) : LoggedWorkout {
        override val finishedAtEpochMillis: Long get() = entry.finishedAtEpochMillis
    }

    data class Lift(val workout: LiftWorkout) : LoggedWorkout {
        override val finishedAtEpochMillis: Long get() = workout.finishedAtEpochMillis
    }
}

/** [intervals] and [lifts] together, newest first. */
fun loggedWorkouts(intervals: List<HistoryEntry>, lifts: List<LiftWorkout>): List<LoggedWorkout> =
    (intervals.map(LoggedWorkout::Interval) + lifts.map(LoggedWorkout::Lift))
        .sortedByDescending { it.finishedAtEpochMillis }
