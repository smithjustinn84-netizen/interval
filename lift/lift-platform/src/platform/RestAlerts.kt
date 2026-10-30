package dev.justinsmith.lift.platform

/**
 * What the platform does for a rest between sets, so its end is heard with the app off screen. Android keeps
 * the process awake with a foreground service whose notification counts the rest down, while the session's
 * own clock keeps ticking and beeping as it does on screen. iOS, which suspends the app, schedules one
 * notification for the end of the rest (shown only if the app is still away, and once notifications are
 * allowed). Desktop does nothing: its window never sleeps.
 */
interface RestAlerts {
    /** A rest started, to end at [endAtEpochMillis] on the wall clock; a rest already kept is replaced. */
    fun start(endAtEpochMillis: Long)

    /** The rest ended, or was skipped: nothing more to show or play. */
    fun stop()
}
