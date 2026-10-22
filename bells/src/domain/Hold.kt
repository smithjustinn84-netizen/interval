package dev.justinsmith.bells.domain

/** Where a timed hold stands: counting in, holding, or over. */
enum class HoldPhase { GetReady, Hold, Done }

/** A timed hold's state at one moment: [secondsLeft] in the count-in or the hold, rounded up. */
data class HoldState(val phase: HoldPhase, val secondsLeft: Int, val progress: Float)

/**
 * A hold of [seconds] after a [countInMillis] count-in, as a pure function of time: [at] gives the state
 * [elapsedMillis] after it started. Its cues are an EMOM's: 3-2-1 into the hold, Start, 3-2-1 out, Finished.
 */
class HoldClock(val seconds: Int, private val countInMillis: Long = COUNT_IN_MILLIS) {
    private val holdMillis = seconds * SECOND_MILLIS
    private val endMillis = countInMillis + holdMillis

    fun at(elapsedMillis: Long): HoldState = when {
        elapsedMillis < countInMillis -> HoldState(
            HoldPhase.GetReady,
            secondsUp(countInMillis - elapsedMillis),
            elapsedMillis.toFloat() / countInMillis,
        )

        elapsedMillis < endMillis -> HoldState(
            HoldPhase.Hold,
            secondsUp(endMillis - elapsedMillis),
            (elapsedMillis - countInMillis).toFloat() / holdMillis,
        )

        else -> HoldState(HoldPhase.Done, 0, 1f)
    }

    /** The whole seconds held [elapsedMillis] after it started: none in the count-in, at most [seconds]. */
    fun heldSeconds(elapsedMillis: Long): Int =
        ((elapsedMillis - countInMillis) / SECOND_MILLIS).toInt().coerceIn(0, seconds)

    /** The cues due after [fromMillis] up to [toMillis]: 3-2-1 and Start into the hold, 3-2-1 and Finished out. */
    fun cuesBetween(fromMillis: Long, toMillis: Long): List<EmomCue> {
        if (toMillis <= fromMillis) return emptyList()
        val due = (fromMillis + 1)..toMillis
        val cues = mutableListOf<EmomCue>()
        for ((start, cue) in listOf(countInMillis to EmomCue.Start, endMillis to EmomCue.Finished)) {
            for (s in COUNTDOWN downTo 1) {
                val at = start - s * SECOND_MILLIS
                if (at in due && at >= 0) cues += EmomCue.Countdown
            }
            if (start in due) cues += cue
        }
        return cues
    }

    private fun secondsUp(millis: Long) = ((millis + SECOND_MILLIS - 1) / SECOND_MILLIS).toInt()

    companion object {
        /** Three seconds to get down: the count-in is the 3-2-1 itself. */
        const val COUNT_IN_MILLIS = 3_000L
        private const val SECOND_MILLIS = 1_000L
        private const val COUNTDOWN = 3
    }
}
