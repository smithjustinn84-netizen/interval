package dev.justinsmith.lift.data

import kotlinx.coroutines.flow.Flow

/**
 * Logging a day of the program followed after the fact, as the Log does for a day trained without the phone: the
 * program's next day done as planned (`doneAsPlanned`), each set at the load it would have been suggested, so its
 * place, the week and the streak go on as if the session had been run. Lift's `SessionRepository` is it.
 */
interface LiftLogging {
    /**
     * When the program followed was taken up, its days counted from then; null with none followed. A session dated
     * before it would not count as one of its days, so none is offered then.
     */
    val followedSince: Flow<Long?>

    /**
     * Logs the followed program's next day ([LiftActivity.programDay]) done as planned under [title], finished at
     * [finishedAtEpochMillis], beside any session in progress; returns its uid, or null with no day to log.
     */
    fun logDayAsPlanned(title: String, finishedAtEpochMillis: Long): String?

    /** Takes back a session [logDayAsPlanned] logged. */
    fun unlog(uid: String)
}
