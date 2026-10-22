package dev.justinsmith.bells.session

import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.HoldClock
import dev.justinsmith.bells.domain.HoldPhase
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.other
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A hold being timed: the set as the sheet had it, when it started, and whether it is the first side. */
data class HoldRun(val set: SetDraft, val startedAtMillis: Long, val firstSide: Boolean) {
    val clock: HoldClock get() = HoldClock(set.seconds)
}

/** The other side of a one-sided hold just timed, to time next for the same length. */
data class HoldOffer(val exercise: Exercise, val side: Side)

/** The hold being timed, if one is, and once a first side is done the other side on offer. */
data class HoldTiming(val run: HoldRun? = null, val offer: HoldOffer? = null)

/**
 * Times a hold from the Log sheet: counts in, counts down, beeps like an EMOM and [log]s the set on the last
 * beep, or the whole seconds held when stopped early. [tick] moves it on; the view model calls it with its
 * other cues.
 */
class HoldTimer(
    private val clock: WallClock,
    private val beeper: Beeper,
    private val log: (set: SetDraft, seconds: Int) -> Unit,
) {
    private val timing = MutableStateFlow(HoldTiming())
    val state: StateFlow<HoldTiming> = timing.asStateFlow()

    /** Before the start, so the count-in's first beep plays. */
    private var lastCueMillis = BEFORE_START

    /** Starts timing [set]: the other side of the hold just done, if that is what is on offer. */
    fun start(set: SetDraft) {
        if (!set.held) return
        val otherSide = timing.value.offer == HoldOffer(set.exercise, set.side)
        lastCueMillis = BEFORE_START
        timing.value = HoldTiming(HoldRun(set, clock.nowMillis(), firstSide = !otherSide))
    }

    /** Stops early, logging the whole seconds held: none in the count-in. */
    fun stop() {
        val run = timing.value.run ?: return
        end(run, run.clock.heldSeconds(clock.nowMillis() - run.startedAtMillis))
    }

    /** Stops without logging. */
    fun cancel() {
        timing.value = HoldTiming()
    }

    fun tick(now: Long) {
        val run = timing.value.run ?: return
        val elapsed = now - run.startedAtMillis
        run.clock.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (run.clock.at(elapsed).phase == HoldPhase.Done) end(run, run.set.seconds)
    }

    private fun end(run: HoldRun, seconds: Int) {
        val set = run.set
        val offer = HoldOffer(set.exercise, set.side.other)
            .takeIf { seconds > 0 && run.firstSide && set.exercise.sided(set.doubled) }
        // Cleared first, so a tick that lands while logging can't log it twice.
        timing.value = HoldTiming(offer = offer)
        if (seconds > 0) log(set, seconds)
    }

    private companion object {
        const val BEFORE_START = -1L
    }
}
