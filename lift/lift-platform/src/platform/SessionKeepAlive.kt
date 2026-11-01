package dev.justinsmith.lift.platform

import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.HoldClock

/** A run of one of the session's timers, on the wall clock: when it started and when it ends. */
sealed interface TimerRun {
    val startedAtEpochMillis: Long
    val endAtEpochMillis: Long

    /** A rest between sets. */
    data class Rest(override val startedAtEpochMillis: Long, override val endAtEpochMillis: Long) : TimerRun

    /** An EMOM of [minutes] minutes, over once its count-in and minutes are. */
    data class Emom(override val startedAtEpochMillis: Long, val minutes: Int) : TimerRun {
        override val endAtEpochMillis: Long get() = startedAtEpochMillis + EmomClock(minutes).lengthMillis
    }

    /** A hold of [seconds], over once its count-in and seconds are. */
    data class Hold(override val startedAtEpochMillis: Long, val seconds: Int) : TimerRun {
        override val endAtEpochMillis: Long
            get() = startedAtEpochMillis + HoldClock.COUNT_IN_MILLIS + seconds * MILLIS_PER_SECOND
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * What the platform does for a run of the session's timers (a rest, an EMOM or a hold), so its cues are heard
 * with the app off screen. Android keeps the process awake with a foreground service whose notification counts
 * the run down, while the session's own timers keep ticking and beeping as they do on screen. iOS, which
 * suspends the app, schedules one notification for the end of the run (shown only if the app is still away, and
 * once notifications are allowed). Desktop does nothing: its window never sleeps.
 */
interface SessionKeepAlive {
    /** Keeps [run], in place of any run already kept: a rest made longer, or an EMOM started over a rest. */
    fun start(run: TimerRun)

    /** The run ended, or was skipped: nothing more to show or play. */
    fun stop()
}

/**
 * The session's timers as the platform reaches them: Android's keep-alive service ends them when the user swipes
 * the app away, and the app when the session's page leaves the back stack. `lift-session`'s `SessionTimers` is it.
 */
fun interface ActiveTimers {
    /** Ends whatever run is going, without logging it, and lets the keep-alive go. */
    fun stopAll()
}
