package dev.justinsmith.interval.timer

/** One interval of a workout laid end to end with the others: what it is and how long, in whole seconds. */
data class OutlinedInterval(val phase: Phase, val round: Int, val seconds: Int, val set: Int = 1, val block: Int = 0)

/**
 * The whole workout as it stands, for a bar of it: every interval in order, and how far into them it is. The
 * current interval has its own length, with any time added; the others the configuration's. Whole seconds, so
 * it changes once a second, with the clock.
 */
data class WorkoutOutline(val intervals: List<OutlinedInterval>, val elapsedSeconds: Int) {
    val totalSeconds: Int get() = intervals.sumOf { it.seconds }

    companion object {
        val Empty = WorkoutOutline(emptyList(), 0)

        /**
         * The outline of the workout at [state]. Positional: the intervals before the current one count at their
         * planned length however long they ran, so a skip moves the point to the next interval's start and Back
         * to the one before. Over, it is the whole bar.
         */
        fun of(state: WorkoutState): WorkoutOutline {
            val intervals = mutableListOf<OutlinedInterval>()
            var elapsed = 0
            Course.of(state.config).forEach { planned ->
                val now = planned.comparedTo(state) == 0
                val seconds = if (now) (state.phaseMillis / MILLIS_PER_SECOND).toInt() else planned.seconds
                val interval = OutlinedInterval(planned.phase, planned.round, seconds, planned.set, planned.block)
                if (now) {
                    val into = ((state.phaseMillis - state.remainingMillis) / MILLIS_PER_SECOND).toInt()
                    elapsed = intervals.sumOf { it.seconds } + into.coerceIn(0, interval.seconds)
                }
                intervals += interval
            }
            val outline = WorkoutOutline(intervals, elapsed)
            return if (state.isDone) outline.copy(elapsedSeconds = outline.totalSeconds) else outline
        }
    }
}
