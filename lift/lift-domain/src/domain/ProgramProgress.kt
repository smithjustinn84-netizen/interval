package dev.justinsmith.lift.domain

/**
 * The day of its program each session of it was, from [days], the day each finished session was logged as,
 * oldest first: as logged, or for one logged before sessions kept their day (null) the day after the one
 * before it, the first after [daysBefore]. So the days logged before this build still count in turn, and
 * deleting one of them still moves the ones after it; a day logged since keeps its number whatever goes.
 */
fun dayNumbers(days: List<Int?>, daysBefore: Int): List<Int> =
    days.runningFold(daysBefore) { last, day -> day ?: (last + 1) }.drop(1)

/** The last day of a program done: the highest of [dayNumbers], or [daysBefore] with none. */
fun lastDayDone(days: List<Int?>, daysBefore: Int): Int = dayNumbers(days, daysBefore).maxOrNull() ?: daysBefore

/**
 * How a day of a program stands: done before it was followed (a copy's days under its original), done, missed
 * (below a day done, with no session), the one to do next, or still to come.
 */
enum class DayStanding { DoneBefore, Done, Missed, Next, ToCome }

/**
 * A day of a program done: day [number], by [session] (null for one counted done before the program was
 * followed), with [setsDone] of its [setsPlanned] done, planned as the session's plan had the day.
 */
data class DayDone(val number: Int, val session: LiftSession?, val setsDone: Int, val setsPlanned: Int)

/**
 * Where someone stands in the program followed: its days [done] oldest first, the day to do [next] (null once
 * it is over), and what its sessions came to: their [reps], the [tonnageKg] moved and the [records] they set
 * against everything done before the program was followed.
 */
data class ProgramProgress(
    val program: Program,
    val done: List<DayDone>,
    val next: ProgramDay?,
    val reps: Int,
    val tonnageKg: Double,
    val records: List<PersonalRecord>,
) {
    /** The last day done, 0 before the first. */
    val lastDay: Int get() = done.maxOfOrNull { it.number } ?: 0

    val daysLeft: Int get() = (program.totalDays - lastDay).coerceAtLeast(0)

    val finished: Boolean get() = next == null

    /** How day [number] stands, for the map. */
    fun standing(number: Int): DayStanding {
        val day = done.lastOrNull { it.number == number }
        return when {
            number == next?.number -> DayStanding.Next
            day != null && day.session == null -> DayStanding.DoneBefore
            day != null -> DayStanding.Done
            number < lastDay -> DayStanding.Missed
            else -> DayStanding.ToCome
        }
    }
}

/**
 * Where someone stands in [followed], over [sessions] (finished, newest first, as the repository holds them):
 * the program's sessions since it was followed, numbered as [dayNumbers] does, after [Followed.daysBefore]
 * days counted done with no session. [ProgramProgress.next] is the repository's day: both go by [lastDayDone].
 * The records are those of the run against every session started before it; a free session logged during
 * the run is in neither.
 */
fun programProgress(followed: Followed, sessions: List<LiftSession>): ProgramProgress {
    val program = followed.program
    val since = followed.startedAtEpochMillis
    val run = sessions.filter { it.program == program.key && it.startedAtEpochMillis >= since }.asReversed()
    val numbers = dayNumbers(run.map { it.day }, followed.daysBefore)
    val before = (1..followed.daysBefore).map { DayDone(it, session = null, setsDone = 0, setsPlanned = 0) }
    val done = run.zip(numbers) { session, number ->
        val planned = program.day(number).copy(plan = session.plan).plannedSets()
        DayDone(number, session, plannedDone(planned, session.sets).count { it }, planned.size)
    }
    val setsOfRun = run.flatMap { it.sets }
    return ProgramProgress(
        program,
        done = before + done,
        next = program.dayAfter(lastDayDone(run.map { it.day }, followed.daysBefore)),
        reps = setsOfRun.sumOf { it.reps },
        tonnageKg = run.sumOf { it.tonnageKg },
        records = newRecords(sessions.filter { it.startedAtEpochMillis < since }.flatMap { it.sets }, setsOfRun),
    )
}
