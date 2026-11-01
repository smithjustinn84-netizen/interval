package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.platform.Beeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A rest between sets: when it started, on the [RunClock]'s monotonic time and on the wall clock, and how long
 * it lasts.
 */
data class RestRun(val startedAtMillis: Long, val seconds: Int, val startedAtEpochMillis: Long) {
    val clock: RestClock get() = RestClock(seconds)
}

/** Counts a rest down after a set: beeps 3-2-1 and Start as it ends, then is over. [tick] moves it on. */
class RestTimer(private val clock: RunClock, private val beeper: Beeper) {
    private val run = MutableStateFlow<RestRun?>(null)
    val state: StateFlow<RestRun?> = run.asStateFlow()

    private var lastCueMillis = 0L

    /** Starts a rest of [seconds] from now, in place of any already running. */
    fun start(seconds: Int) {
        lastCueMillis = 0L
        run.value = RestRun(clock.now(), seconds, clock.epochNow())
    }

    /** Goes on with [rest], started before the process was: no cue for the time that passed. */
    fun resume(rest: RestRun) {
        lastCueMillis = clock.now() - rest.startedAtMillis
        run.value = rest
    }

    /** Makes the rest running [seconds] long, from when it started: over at the next tick if that is past. */
    fun adjust(seconds: Int) {
        val rest = run.value ?: return
        run.value = rest.copy(seconds = seconds)
    }

    /** Ends the rest early, without a cue. */
    fun skip() {
        run.value = null
    }

    fun tick(now: Long) {
        val rest = run.value ?: return
        val elapsed = now - rest.startedAtMillis
        rest.clock.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (rest.clock.isDone(elapsed)) run.value = null
    }
}
