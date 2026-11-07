package dev.justinsmith.lift.domain

/** Where a density block stands: counting in, working against the cap, or over. */
enum class DensityPhase { GetReady, Work, Done }

/**
 * A density block's state at one moment: [secondsLeft] in the count-in or of the cap, rounded up, [rounds] done
 * so far of the [minutes] it is capped at, and how far through the count-in or the cap it is ([progress], 0 to 1).
 */
data class DensityState(
    val phase: DensityPhase,
    val secondsLeft: Int,
    val rounds: Int,
    val minutes: Int,
    val progress: Float,
)

/**
 * A density block capped at [minutes] after a [countInMillis] count-in, as a pure function of time: [EmomClock]'s
 * count-in, then one countdown with no minutes to rest out. [at] gives the state [elapsedMillis] after it started,
 * with the [rounds] done so far, which only the lifter's taps say.
 */
class DensityClock(val minutes: Int, private val countInMillis: Long = EmomClock.COUNT_IN_MILLIS) {
    /** When the cap ends and the block is done. */
    val lengthMillis: Long get() = countInMillis + minutes * EmomClock.MINUTE_MILLIS

    /** True once the block has been done for [EmomClock.DONE_HOLD_MILLIS]: time to leave Done on screen, then go. */
    fun isOver(elapsedMillis: Long): Boolean = elapsedMillis >= lengthMillis + EmomClock.DONE_HOLD_MILLIS

    fun at(elapsedMillis: Long, rounds: Int): DensityState = when {
        elapsedMillis < countInMillis -> DensityState(
            DensityPhase.GetReady,
            secondsUp(countInMillis - elapsedMillis),
            rounds,
            minutes,
            elapsedMillis.toFloat() / countInMillis,
        )

        elapsedMillis >= lengthMillis -> DensityState(DensityPhase.Done, 0, rounds, minutes, 1f)

        else -> DensityState(
            DensityPhase.Work,
            secondsUp(lengthMillis - elapsedMillis),
            rounds,
            minutes,
            (elapsedMillis - countInMillis).toFloat() / (minutes * EmomClock.MINUTE_MILLIS),
        )
    }

    /**
     * The cues due after [fromMillis] up to [toMillis]: 3-2-1 and Start out of the count-in; in the cap's last ten
     * seconds a tone at ten, then 3-2-1 and Finished as it ends.
     */
    fun cuesBetween(fromMillis: Long, toMillis: Long): List<EmomCue> {
        if (toMillis <= fromMillis) return emptyList()
        val due = buildList {
            for (s in EmomClock.COUNTDOWN downTo 1) {
                add(
                    countInMillis - s * EmomClock.SECOND_MILLIS to EmomCue.Countdown,
                )
            }
            add(countInMillis to EmomCue.Start)
            add(lengthMillis - WARNING_SECONDS * EmomClock.SECOND_MILLIS to EmomCue.Countdown)
            for (s in EmomClock.COUNTDOWN downTo 1) add(lengthMillis - s * EmomClock.SECOND_MILLIS to EmomCue.Countdown)
            add(lengthMillis to EmomCue.Finished)
        }
        return due.filter { (at, _) -> at >= 0 && at in (fromMillis + 1)..toMillis }.map { it.second }
    }

    private fun secondsUp(millis: Long) =
        ((millis + EmomClock.SECOND_MILLIS - 1) / EmomClock.SECOND_MILLIS).toInt()

    companion object {
        /** The shortest cap and the longest, in minutes: the ABC's second protocol runs 15 to 20. */
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 30

        /** The tone that says the cap is nearly up, this many seconds before it ends. */
        const val WARNING_SECONDS = 10
    }
}
