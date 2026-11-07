package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.DensityClock
import dev.justinsmith.lift.domain.DensityPhase
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.double
import dev.justinsmith.lift.platform.Beeper
import dev.justinsmith.lift.platform.Coaching
import dev.justinsmith.lift.platform.LiftVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A density block under way: its lift, load and a round's reps, the [minutes] it is capped at, when it started (on
 * the [RunClock]'s monotonic time, and on the wall clock), the [rounds] done so far, and the [best] rounds of the
 * same block before (null the first time).
 */
data class DensityRun(
    val exercise: Exercise,
    val load: Load,
    val reps: Int,
    val minutes: Int,
    val firstSide: Side,
    val startedAtMillis: Long,
    val rounds: Int = 0,
    val startedAtEpochMillis: Long = 0L,
    val best: Int? = null,
) {
    /** Whether it is done with two bells. */
    val double: Boolean get() = load.double

    /** The side of round [round] (from 1): a one-sided lift with one bell alternates, as an EMOM's minutes do. */
    fun sideOf(round: Int): Side? = if (exercise.sided(double)) {
        Side.entries[(firstSide.ordinal + round - 1) % Side.entries.size]
    } else {
        null
    }
}

/**
 * Runs a density block: starts it, [log]s each round as it is done, beeps its cues and ends it once the cap is up
 * and Done has shown. [tick] moves it on. Its [voice] says one of the lift's cues as it counts in, then only the
 * clock: five minutes left, one, and time with the rounds done. Never a word as a round is done, so it never
 * talks over the next.
 */
class DensityRunner(
    private val clock: RunClock,
    private val beeper: Beeper,
    private val voice: LiftVoice,
    private val log: (run: DensityRun, round: Int) -> Unit,
) {
    private val run = MutableStateFlow<DensityRun?>(null)
    val state: StateFlow<DensityRun?> = run.asStateFlow()
    private var lastCueMillis = 0L

    @Suppress("detekt.LongParameterList") // A block's own: its lift, load, reps and cap, a side and its best.
    fun start(exercise: Exercise, load: Load, reps: Int, minutes: Int, firstSide: Side, best: Int?) {
        lastCueMillis = 0L
        run.value = DensityRun(
            exercise,
            load,
            reps,
            minutes,
            firstSide,
            clock.now(),
            startedAtEpochMillis = clock.epochNow(),
            best = best,
        )
        voice.say(Coaching.Cue(exercise, 0))
    }

    /** Goes on with [density], started before the process was: no cue for the time that passed. */
    fun resume(density: DensityRun) {
        lastCueMillis = clock.now() - density.startedAtMillis
        run.value = density
    }

    /** Logs a round, while the cap runs: before it starts and once it is up, a tap does nothing. */
    fun roundDone() {
        val current = run.value ?: return
        if (DensityClock(current.minutes).at(clock.now() - current.startedAtMillis, current.rounds).phase !=
            DensityPhase.Work
        ) {
            return
        }
        val round = current.rounds + 1
        log(current, round)
        run.value = current.copy(rounds = round)
    }

    fun stop() {
        run.value = null
    }

    /** Beeps whatever cue came due since the last tick, says the clock's call if one did, and ends it once over. */
    fun tick(now: Long) {
        val current = run.value ?: return
        val clockOfRun = DensityClock(current.minutes)
        val elapsed = now - current.startedAtMillis
        clockOfRun.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        clockOfRun.callsBetween(lastCueMillis, elapsed).lastOrNull()?.let { call ->
            voice.say(Coaching.Clock(current.exercise, call, current.rounds))
        }
        lastCueMillis = elapsed
        if (clockOfRun.isOver(elapsed)) run.value = null
    }
}
