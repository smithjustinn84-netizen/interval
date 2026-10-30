package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import kotlinx.coroutines.flow.StateFlow

/**
 * What the rest of the app reads of Lift, the mirror of [IntervalActivity]: History and Plan count its sessions
 * in the week, History shows the program's next day and starts it, and the workout's summary counts the days
 * lifted. [BellsRepository] is it; screens outside Lift take this rather than the whole repository.
 */
interface LiftActivity {
    /** Finished sessions, newest first. */
    val sessions: StateFlow<List<LiftSession>>

    /** The day of the program followed to do next; null when none is followed or it is over. */
    val programDay: StateFlow<ProgramDay?>

    /** The session in progress, if one is. */
    val active: StateFlow<LiftSession?>

    /** Starts a session, a day of [program] or a free one, unless one is in progress already. */
    fun startSession(program: ProgramKey? = null)
}
