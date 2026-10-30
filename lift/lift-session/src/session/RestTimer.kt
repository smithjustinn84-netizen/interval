package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.platform.Beeper
import dev.justinsmith.lift.platform.RestAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A rest between sets: when it started and how long it lasts. */
data class RestRun(val startedAtMillis: Long, val seconds: Int) {
    val clock: RestClock get() = RestClock(seconds)
}

/**
 * Counts a rest down after a set: beeps 3-2-1 and Start as it ends, then is over. [tick] moves it on; the view
 * model calls it with its other cues. The platform keeps the rest too ([alerts]), for when the app is off screen.
 */
class RestTimer(private val clock: WallClock, private val beeper: Beeper, private val alerts: RestAlerts) {
    private val run = MutableStateFlow<RestRun?>(null)
    val state: StateFlow<RestRun?> = run.asStateFlow()

    private var lastCueMillis = 0L

    /** Starts a rest of [seconds] from now, in place of any already running. */
    fun start(seconds: Int) {
        lastCueMillis = 0L
        val startedAt = clock.nowMillis()
        run.value = RestRun(startedAt, seconds)
        alerts.start(startedAt + seconds * MILLIS_PER_SECOND)
    }

    /** Makes the rest running [seconds] long, from when it started: over at once if that is already past. */
    fun adjust(seconds: Int) {
        val rest = run.value ?: return
        run.value = rest.copy(seconds = seconds)
        alerts.start(rest.startedAtMillis + seconds * MILLIS_PER_SECOND)
    }

    /** Ends the rest early, without a cue. */
    fun skip() {
        if (run.value == null) return
        run.value = null
        alerts.stop()
    }

    fun tick(now: Long) {
        val rest = run.value ?: return
        val elapsed = now - rest.startedAtMillis
        rest.clock.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (rest.clock.isDone(elapsed)) {
            run.value = null
            alerts.stop()
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
