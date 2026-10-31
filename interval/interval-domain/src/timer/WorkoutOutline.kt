package dev.justinsmith.interval.timer

/** One interval of a workout laid end to end with the others: what it is and how long, in whole seconds. */
data class OutlinedInterval(val phase: Phase, val round: Int, val seconds: Int)

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
            var current = WorkoutEngine.start(state.config).state
            while (!current.isDone) {
                val now = current.phase == state.phase && current.round == state.round
                val millis = if (now) state.phaseMillis else current.phaseMillis
                val interval = OutlinedInterval(current.phase, current.round, (millis / MILLIS_PER_SECOND).toInt())
                if (now) {
                    val into = ((state.phaseMillis - state.remainingMillis) / MILLIS_PER_SECOND).toInt()
                    elapsed = intervals.sumOf { it.seconds } + into.coerceIn(0, interval.seconds)
                }
                intervals += interval
                current = WorkoutEngine.advance(current).state
            }
            val outline = WorkoutOutline(intervals, elapsed)
            return if (state.isDone) outline.copy(elapsedSeconds = outline.totalSeconds) else outline
        }
    }
}
