package dev.justinsmith.bells.session

import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An EMOM under way: its lift, bell (or two, when [double]) and reps, when it started, and the minutes whose
 * set is done.
 */
data class EmomRun(
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val minutes: Int,
    val firstSide: Side,
    val startedAtMillis: Long,
    val done: Set<Int> = emptySet(),
    val double: Boolean = false,
    /** The band, for an exercise done with one. */
    val band: Band? = null,
) {
    /** The side of minute [minute] (from 1): a one-sided lift with one bell alternates. */
    fun sideOf(minute: Int): Side? = if (exercise.sided(double)) {
        Side.entries[(firstSide.ordinal + minute - 1) % Side.entries.size]
    } else {
        null
    }
}

/**
 * Runs an EMOM: starts it, [log]s each minute's set as it is done, beeps its cues and ends it once its minutes
 * are up. [tick] moves it on; the view model calls it with its other cues.
 */
class EmomRunner(
    private val clock: WallClock,
    private val beeper: Beeper,
    private val log: (run: EmomRun, minute: Int) -> Unit,
) {
    private val run = MutableStateFlow<EmomRun?>(null)
    val state: StateFlow<EmomRun?> = run.asStateFlow()
    private var lastCueMillis = 0L

    fun start(
        exercise: Exercise,
        bellKg: Double,
        reps: Int,
        minutes: Int,
        firstSide: Side,
        double: Boolean,
        band: Band? = null,
    ) {
        lastCueMillis = 0L
        run.value = EmomRun(exercise, bellKg, reps, minutes, firstSide, clock.nowMillis(), double = double, band = band)
    }

    /** Logs this minute's set, in its work phase: the EMOM rests out the minute. */
    fun setDone() {
        val current = run.value ?: return
        val now = EmomClock(current.minutes).at(clock.nowMillis() - current.startedAtMillis, current.done)
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
