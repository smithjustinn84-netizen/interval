package dev.justinsmith.lift.domain

/** A rest between sets at one moment: [secondsLeft] of its [seconds], rounded up, and how far through it is. */
data class RestState(val seconds: Int, val secondsLeft: Int, val progress: Float)

/**
 * A rest of [seconds] between sets, as a pure function of time: [at] gives the state [elapsedMillis] after it
 * started. Its cues are an EMOM's into a minute: 3-2-1, then Start as it ends.
 */
class RestClock(val seconds: Int) {
    private val endMillis = seconds * SECOND_MILLIS

    fun at(elapsedMillis: Long): RestState {
        val left = (endMillis - elapsedMillis).coerceAtLeast(0)
        return RestState(
            seconds,
            ((left + SECOND_MILLIS - 1) / SECOND_MILLIS).toInt(),
            (elapsedMillis.toFloat() / endMillis).coerceIn(0f, 1f),
        )
    }

    fun isDone(elapsedMillis: Long): Boolean = elapsedMillis >= endMillis

    /** The cues due after [fromMillis] up to [toMillis]: 3-2-1, and Start as the rest ends. */
    fun cuesBetween(fromMillis: Long, toMillis: Long): List<EmomCue> {
        if (toMillis <= fromMillis) return emptyList()
        val due = (fromMillis + 1)..toMillis
        val cues = mutableListOf<EmomCue>()
        for (s in COUNTDOWN downTo 1) {
            val at = endMillis - s * SECOND_MILLIS
            if (at in due && at >= 0) cues += EmomCue.Countdown
        }
        if (endMillis in due) cues += EmomCue.Start
        return cues
    }

    companion object {
        /** A rest is a minute and a half until the user says otherwise. */
        const val DEFAULT_SECONDS = 90

        /** −/+ step a rest by fifteen seconds, from fifteen up to ten minutes. */
        const val STEP_SECONDS = 15
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 600
        private const val SECOND_MILLIS = 1_000L
        private const val COUNTDOWN = 3
    }
}
