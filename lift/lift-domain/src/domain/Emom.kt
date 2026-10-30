package dev.justinsmith.lift.domain

/** Where an EMOM stands: counting in, working the minute's reps, resting out the minute, or over. */
enum class EmomPhase { GetReady, Work, Rest, Done }

/** An EMOM's state at one moment: [minute] from 1, [secondsLeft] in the count-in or the minute, rounded up. */
data class EmomState(
    val phase: EmomPhase,
    val minute: Int,
    val minutes: Int,
    val secondsLeft: Int,
    val minuteProgress: Float,
)

/** What to play as an EMOM moves on. */
enum class EmomCue { Countdown, Start, Finished }

/**
 * An EMOM of [minutes] minutes after a [countInMillis] count-in, as a pure function of time: [at] gives the
 * state [elapsedMillis] after it started, given which minutes have their set [done] (from 1).
 */
class EmomClock(val minutes: Int, private val countInMillis: Long = COUNT_IN_MILLIS) {
    /** When the last minute ends and the EMOM is done. */
    val lengthMillis: Long get() = countInMillis + minutes * MINUTE_MILLIS

    /** True once the EMOM has been done for [DONE_HOLD_MILLIS]: time to leave Done on screen, then go. */
    fun isOver(elapsedMillis: Long): Boolean = elapsedMillis >= lengthMillis + DONE_HOLD_MILLIS

    fun at(elapsedMillis: Long, done: Set<Int>): EmomState {
        if (elapsedMillis < countInMillis) {
            return EmomState(
                EmomPhase.GetReady,
                1,
                minutes,
                secondsUp(countInMillis - elapsedMillis),
                elapsedMillis.toFloat() / countInMillis,
            )
        }
        val running = elapsedMillis - countInMillis
        val minute = (running / MINUTE_MILLIS).toInt() + 1
        if (minute > minutes) return EmomState(EmomPhase.Done, minutes, minutes, 0, 1f)
        val intoMinute = running % MINUTE_MILLIS
        val phase = if (minute in done) EmomPhase.Rest else EmomPhase.Work
        return EmomState(
            phase,
            minute,
            minutes,
            secondsUp(MINUTE_MILLIS - intoMinute),
            intoMinute.toFloat() / MINUTE_MILLIS,
        )
    }

    /** The cues due after [fromMillis] up to [toMillis]: 3-2-1, each minute's start, and the end. */
    fun cuesBetween(fromMillis: Long, toMillis: Long): List<EmomCue> {
        if (toMillis <= fromMillis) return emptyList()
        val cues = mutableListOf<EmomCue>()
        for (m in 0..minutes) {
            val start = countInMillis + m * MINUTE_MILLIS
            for (s in COUNTDOWN downTo 1) {
                val at = start - s * SECOND_MILLIS
                if (at in (fromMillis + 1)..toMillis && at >= 0) cues += EmomCue.Countdown
            }
            if (start in (fromMillis + 1)..toMillis) cues += if (m == minutes) EmomCue.Finished else EmomCue.Start
        }
        return cues
    }

    private fun secondsUp(millis: Long) = ((millis + SECOND_MILLIS - 1) / SECOND_MILLIS).toInt()

    companion object {
        const val MINUTE_MILLIS = 60_000L
        const val SECOND_MILLIS = 1_000L
        const val COUNT_IN_MILLIS = 10_000L

        /** How many seconds before each minute get a beep: the last three. */
        const val COUNTDOWN = 3

        /** Done stays on screen this long before the session comes back. */
        const val DONE_HOLD_MILLIS = 1_500L
    }
}
