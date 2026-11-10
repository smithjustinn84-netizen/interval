package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.ReadinessReason
import kotlinx.coroutines.flow.Flow

/**
 * How ready the lifter is, as the rest of the app reads it of Lift: Today says why a day is one to take easier
 * and starts the program's day eased. Lift's own reading (`readiness`, from the sessions finished, the BJJ logged
 * and the grip), so both sides say the same of a day; lift-session's `EasierDay` is it.
 */
interface LiftReadiness {
    /** Why today is a day to take easier; null on any other day. Read as it is collected, by the clock then. */
    val taxedBy: Flow<ReadinessReason?>

    /**
     * Whether the followed program's next day has an easier plan to offer before it starts: on a taxed day, with
     * no session in progress, and only where easing changes the day.
     */
    val easierAhead: Flow<Boolean>

    /** Makes the plan of the session in progress easier, as offered: after `LiftActivity.startSession`. */
    fun useEasier()
}
