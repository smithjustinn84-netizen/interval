package dev.justinsmith.bells.session

import dev.justinsmith.bells.domain.RestClock
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A rest between sets: when it started and how long it lasts. */
data class RestRun(val startedAtMillis: Long, val seconds: Int) {
    val clock: RestClock get() = RestClock(seconds)
}

/**
 * Counts a rest down after a set: beeps 3-2-1 and Start as it ends, then is over. [tick] moves it on; the view
 * model calls it with its other cues.
 */
class RestTimer(private val clock: WallClock, private val beeper: Beeper) {
    private val run = MutableStateFlow<RestRun?>(null)
    val state: StateFlow<RestRun?> = run.asStateFlow()

    private var lastCueMillis = 0L

    /** Starts a rest of [seconds] from now, in place of any already running. */
    fun start(seconds: Int) {
        lastCueMillis = 0L
        run.value = RestRun(clock.nowMillis(), seconds)
    }

    /** Makes the rest running [seconds] long, from when it started: over at once if that is already past. */
    fun adjust(seconds: Int) {
        run.value = run.value?.copy(seconds = seconds)
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
