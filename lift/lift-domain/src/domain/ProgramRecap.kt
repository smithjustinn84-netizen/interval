package dev.justinsmith.lift.domain

/**
 * A lift of a finished program as it started and ended: the best working set of its [first] session in the run and
 * of its [last], one bell or two ([double]). Best is the heaviest, then the most reps; a hold's the longest, then
 * the heaviest.
 */
data class LiftChange(val exercise: Exercise, val double: Boolean, val first: SetLog, val last: SetLog)

/** Why a finished program's next cycle starts with a few easy days. */
enum class EaseReason {
    /** Too much of the plan went undone: days missed, or sets left. */
    MissedWork,

    /** Its sessions were said to feel very hard or worse, on average. */
    HardGoing,
}

/** What to do once a program is over, as a coach would put it. */
sealed interface NextCycle {
    /**
     * Run it again. [fromLast] when the run starts from the loads last lifted, which a program naming no load
     * does (each lift's load is suggested from its history): the bells finished on, not those started on.
     */
    data class GoAgain(val fromLast: Boolean) : NextCycle

    /** A few easy days, then run it again: for [reason]. */
    data class EaseOff(val reason: EaseReason) : NextCycle

    /** A beginner's program done well: move on to [program], one for what is owned and the time a day took. */
    data class MoveOn(val program: Program) : NextCycle
}

/**
 * What a finished program came to: its days done with a session and those [missed], the planned sets done of
 * those planned (a missed day's all undone), the [lifts] as they started and ended, and the [next] cycle. Days
 * counted done before the program was followed have no session, and count toward none of it.
 */
data class ProgramRecap(
    val progress: ProgramProgress,
    val sessions: Int,
    val missed: Int,
    val setsDone: Int,
    val setsPlanned: Int,
    val lifts: List<LiftChange>,
    val next: NextCycle,
) {
    /** The share of the planned sets done, 0 to 1; 1 with none planned. */
    val completion: Double get() = if (setsPlanned == 0) 1.0 else setsDone.toDouble() / setsPlanned
}

/**
 * The recap of [progress] once it is [finished][ProgramProgress.finished], else null. A move on is offered only
 * from a beginner's program done well, to one of the catalogue (barbell programs only with a bar) that [gear] can
 * do and whose longest day fits the time this program's longest took, resting [restSeconds] between sets.
 */
fun programRecap(progress: ProgramProgress, gear: Gear, restSeconds: Int): ProgramRecap? {
    if (!progress.finished) return null
    val program = progress.program
    val done = progress.done.filter { it.session != null }
    val missedDays = (1..program.totalDays).filter { progress.standing(it) == DayStanding.Missed }
    val setsDone = done.sumOf { it.setsDone }
    val setsPlanned = done.sumOf { it.setsPlanned } + missedDays.sumOf { program.day(it).plannedSets().size }
    val sessions = done.mapNotNull { it.session }
    val recap = ProgramRecap(
        progress,
        sessions = sessions.size,
        missed = missedDays.size,
        setsDone = setsDone,
        setsPlanned = setsPlanned,
        lifts = liftChanges(sessions),
        next = NextCycle.GoAgain(fromLast = false),
    )
    return recap.copy(next = nextCycle(recap, sessions, gear, restSeconds))
}

private fun nextCycle(recap: ProgramRecap, sessions: List<LiftSession>, gear: Gear, restSeconds: Int): NextCycle {
    val program = recap.progress.program
    val efforts = sessions.mapNotNull { it.effort?.level }
    return when {
        recap.completion < EASE_OFF_BELOW -> NextCycle.EaseOff(EaseReason.MissedWork)

        efforts.isNotEmpty() && efforts.average() >= PerceivedEffort.VeryHard.level ->
            NextCycle.EaseOff(EaseReason.HardGoing)

        else -> nextProgram(recap, gear, restSeconds)?.let(NextCycle::MoveOn)
            ?: NextCycle.GoAgain(fromLast = program.days.flatten().none { it.load != null })
    }
}

/** For a beginner's program done well, the one to move on to; null for any other. */
private fun nextProgram(recap: ProgramRecap, gear: Gear, restSeconds: Int): Program? {
    val program = recap.progress.program
    val id = (program.key as? ProgramKey.BuiltIn)?.id
    if (id?.family != ProgramFamily.Beginner || recap.completion < MOVE_ON_FROM) return null
    val owned = gear.starterGear()
    val candidates = programCatalogue(bar = owned.barbell)
        .filter { it.key != program.key && (it.key as? ProgramKey.BuiltIn)?.id?.family != ProgramFamily.Beginner }
    return starterProgram(
        minutes = program.dayMinutes(restSeconds).last,
        restSeconds = restSeconds,
        gear = owned,
        programs = candidates,
    )
}

/** What [Gear] says is owned, as a starter program goes by: every body can do bodyweight work. */
fun Gear.starterGear(): StarterGear = StarterGear(
    oneBell = bells.isNotEmpty(),
    pairOfBells = pairs.isNotEmpty(),
    barbell = bar != null,
    bodyweight = true,
)

/**
 * Each lift done in two sessions or more of [sessions] (oldest first), as it started and ended, in the order first
 * done.
 */
private fun liftChanges(sessions: List<LiftSession>): List<LiftChange> {
    val bySession = sessions.map { session ->
        session.workingSets.filter { it.exercise !is UnknownExercise }.groupBy { it.exercise.key to it.double }
    }
    val lifts = bySession.flatMap { it.keys }.distinct()
    return lifts.mapNotNull { lift ->
        val bests = bySession.mapNotNull { sets -> sets[lift]?.let(::bestSet) }
        if (bests.size < 2) null else LiftChange(bests.first().exercise, lift.second, bests.first(), bests.last())
    }.take(MAX_LIFTS)
}

private fun bestSet(sets: List<SetLog>): SetLog = if (sets.first().seconds != null) {
    sets.maxWith(compareBy<SetLog> { it.seconds ?: 0 }.thenBy { it.load.kg })
} else {
    sets.maxWith(compareBy<SetLog> { it.load.kg }.thenBy { it.reps })
}

/** Under this share of the plan done, the next cycle eases in. */
const val EASE_OFF_BELOW = 0.75

/** From this share of a beginner's plan done, the next cycle moves on. */
const val MOVE_ON_FROM = 0.9

/** The most lifts a recap shows as they started and ended. */
const val MAX_LIFTS = 6
