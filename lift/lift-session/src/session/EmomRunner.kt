package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.double
import dev.justinsmith.lift.platform.Beeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An EMOM under way: its lift, load and reps, when it started (on the [RunClock]'s monotonic time, and on the
 * wall clock), and the minutes whose set is done.
 */
data class EmomRun(
    val exercise: Exercise,
    val load: Load,
    val reps: Int,
    val minutes: Int,
    val firstSide: Side,
    val startedAtMillis: Long,
    val done: Set<Int> = emptySet(),
    val startedAtEpochMillis: Long = 0L,
) {
    /** Whether it is done with two bells. */
    val double: Boolean get() = load.double

    /** The side of minute [minute] (from 1): a one-sided lift with one bell alternates. */
    fun sideOf(minute: Int): Side? = if (exercise.sided(double)) {
        Side.entries[(firstSide.ordinal + minute - 1) % Side.entries.size]
    } else {
        null
    }
}

/**
 * Runs an EMOM: starts it, [log]s each minute's set as it is done, beeps its cues and ends it once its minutes
 * are up. [tick] moves it on.
 */
class EmomRunner(
    private val clock: RunClock,
    private val beeper: Beeper,
    private val log: (run: EmomRun, minute: Int) -> Unit,
) {
    private val run = MutableStateFlow<EmomRun?>(null)
    val state: StateFlow<EmomRun?> = run.asStateFlow()
    private var lastCueMillis = 0L

    fun start(exercise: Exercise, load: Load, reps: Int, minutes: Int, firstSide: Side) {
        lastCueMillis = 0L
        run.value = EmomRun(
            exercise,
            load,
            reps,
            minutes,
            firstSide,
            clock.now(),
            startedAtEpochMillis = clock.epochNow(),
        )
    }

    /** Goes on with [emom], started before the process was: no cue for the time that passed. */
    fun resume(emom: EmomRun) {
        lastCueMillis = clock.now() - emom.startedAtMillis
        run.value = emom
    }

    /** Logs this minute's set, in its work phase: the EMOM rests out the minute. */
    fun setDone() {
        val current = run.value ?: return
        val now = EmomClock(current.minutes).at(clock.now() - current.startedAtMillis, current.done)
        if (now.phase != EmomPhase.Work) return
        log(current, now.minute)
        run.value = current.copy(done = current.done + now.minute)
    }

    fun stop() {
        run.value = null
    }

    /** Beeps whatever cue came due since the last tick, and ends the EMOM once its minutes are up. */
    fun tick(now: Long) {
        val current = run.value ?: return
        val clockOfRun = EmomClock(current.minutes)
        val elapsed = now - current.startedAtMillis
        clockOfRun.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (clockOfRun.isOver(elapsed)) run.value = null
    }
}
