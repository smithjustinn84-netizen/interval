package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import kotlinx.coroutines.flow.StateFlow

/**
 * What the rest of the app reads of Lift, the mirror of [IntervalActivity]: History and Plan count its sessions
 * in the week, History and Today show the program's next day, about how long it takes and start it, and the
 * workout's summary counts the days lifted. [LiftRepository] is it; screens outside Lift take this rather than
 * the whole repository.
 */
interface LiftActivity {
    /** True once what is below has been read from storage; until then each holds its default. */
    val ready: StateFlow<Boolean>

    /** Finished sessions, newest first. */
    val sessions: StateFlow<List<LiftSession>>

    /** The day of the program followed to do next; null when none is followed or it is over. */
    val programDay: StateFlow<ProgramDay?>

    /** The session in progress, if one is. */
    val active: StateFlow<LiftSession?>

    /** How long a rest between sets lasts, in seconds: what a day's length is estimated with. */
    val restSeconds: StateFlow<Int>

    /** The bells owned, in kilograms, lightest first: what 15:15's snatches are counted at. */
    val bells: StateFlow<List<Double>>

    /** Starts a session, a day of [program] or a free one, unless one is in progress already. */
    fun startSession(program: ProgramKey?)
}
