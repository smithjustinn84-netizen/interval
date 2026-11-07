package dev.justinsmith.lift.domain

// Benchmarks to chase and totals to keep: all worked out from the sets logged, nothing more stored.

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L

/** The swings the 10,000 Swings challenge counts to. */
const val TEN_THOUSAND = 10_000

/** The 10,000 Swings challenge's window when its program isn't followed: four weeks. */
const val SWING_CHALLENGE_DAYS = 28

/** The snatch test: [SNATCH_TEST_REPS] in [SNATCH_TEST_MINUTES]. */
const val SNATCH_TEST_REPS = 100
const val SNATCH_TEST_MINUTES = 5

/** The window the Armor Building Complex's best is counted in. */
const val ABC_MINUTES = 20

/** The swings in the window before 10,000 Swings is under way, its program not followed. */
const val SWING_CHALLENGE_FROM = 1_000

/** The totals a lift's lifetime count is named at. */
val milestones: List<Int> = listOf(1_000, 5_000, 10_000, 25_000)

/** The swings, one hand or two, that count toward 10,000 together. */
private val swings = setOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.OneHandSwing)

/** Whether [exercise] counts with [other] in a lifetime total: the swings together, any other alone. */
private fun countsWith(exercise: Exercise, other: Exercise): Boolean =
    exercise.key == other.key || (exercise in swings && other in swings)

/** The reps of [exercise] in [sets] (a swing's, both swings'), holds aside. */
fun lifetimeReps(exercise: Exercise, sets: List<SetLog>): Int =
    sets.filter { it.seconds == null && countsWith(exercise, it.exercise) }.sumOf { it.reps }

/** The highest of [milestones] [after] reached that [before] hadn't; null with none crossed. */
fun crossedMilestone(before: Int, after: Int): Int? = milestones.lastOrNull { it in (before + 1)..after }

/**
 * The most reps of [exercise] in any [minutes] of one of [sessions], from sets logged as they were done
 * ([loggedAsDone]): each set's reps counted where it ended. Null with none counted.
 */
fun mostInWindow(exercise: Exercise, sessions: List<LiftSession>, minutes: Int): Int? =
    sessions.mapNotNull { mostInWindow(exercise, it, minutes) }.maxOrNull()

/** The most reps of [exercise] in any [minutes] of [session], as [mostInWindow] counts them; null with none. */
private fun mostInWindow(exercise: Exercise, session: LiftSession, minutes: Int): Int? {
    val sets = session.sets.filter { it.exercise.key == exercise.key && it.seconds == null && it.reps > 0 }
        .sortedBy { it.loggedAtEpochMillis }
    if (sets.isEmpty() || !loggedAsDone(sets)) return null
    val window = minutes * MILLIS_PER_MINUTE
    return sets.indices.maxOf { first ->
        val start = sets[first].loggedAtEpochMillis
        sets.drop(first).takeWhile { it.loggedAtEpochMillis - start < window }.sumOf { it.reps }
    }
}

/**
 * The ABC's best in any [ABC_MINUTES] of [sessions], and the protocol of the session it came from (by its plan, or
 * its day of one of [programs]); the earliest session of a best tied wins. Null with none counted.
 */
fun armorBuildingBest(sessions: List<LiftSession>, programs: List<Program>): Challenge.ArmorBuilding? =
    sessions.sortedBy { it.startedAtEpochMillis }
        .mapNotNull { session ->
            mostInWindow(BuiltInExercise.ArmorBuildingComplex, session, ABC_MINUTES)?.let { session to it }
        }
        .maxByOrNull { it.second }
        ?.let { (session, best) -> Challenge.ArmorBuilding(best, abcProtocolOf(session, programs)) }

/** A benchmark worth chasing, and how far toward it the sets logged have come. */
sealed interface Challenge {
    /** Swings, one hand or two, toward [TEN_THOUSAND]: over the program's run while it is followed, else 28 days. */
    data class TenThousandSwings(val swings: Int, val ofProgram: Boolean) : Challenge {
        val done: Boolean get() = swings >= TEN_THOUSAND
    }

    /** The most snatches in any five minutes of a session, toward [SNATCH_TEST_REPS]. */
    data class SnatchTest(val best: Int) : Challenge {
        val done: Boolean get() = best >= SNATCH_TEST_REPS
    }

    /**
     * The most Armor Building Complexes in any twenty minutes: a best, with no target, and the [protocol] of the
     * session it came from (null for a free session's).
     */
    data class ArmorBuilding(val best: Int, val protocol: AbcProtocol? = null) : Challenge

    /** The heaviest get-up, [kg], as a [share] of the body weight; a whole one is the goal. */
    data class BodyweightGetUp(val kg: Double, val share: Double) : Challenge {
        val done: Boolean get() = share >= 1.0
    }
}

/**
 * The challenges under way over [sessions] by [nowMillis]: 10,000 Swings with [SWING_CHALLENGE_FROM] swings in its
 * window (or while its program, [following], is followed, from [followedSince]), the snatch test and the ABC once
 * either has been done as it was logged (the ABC's by the protocol of its best, its day found among [programs]), and
 * the bodyweight get-up once there is a get-up and a [bodyWeightKg].
 */
@Suppress("detekt.LongParameterList") // What the challenges are counted from.
fun challenges(
    sessions: List<LiftSession>,
    nowMillis: Long,
    following: ProgramKey?,
    followedSince: Long?,
    bodyWeightKg: Double?,
    programs: List<Program> = builtInPrograms,
): List<Challenge> {
    val ofProgram = following == ProgramKey.BuiltIn(ProgramId.TenThousandSwings) && followedSince != null
    val since = if (ofProgram) followedSince else nowMillis - SWING_CHALLENGE_DAYS * MILLIS_PER_DAY
    val sets = sessions.flatMap { it.sets }
    val swung = lifetimeReps(BuiltInExercise.TwoHandSwing, sets.filter { it.loggedAtEpochMillis >= since })
    val getUps = sets.filter { it.exercise == BuiltInExercise.GetUp }
    return listOfNotNull(
        Challenge.TenThousandSwings(swung, ofProgram).takeIf { swung >= SWING_CHALLENGE_FROM || ofProgram },
        mostInWindow(BuiltInExercise.Snatch, sessions, SNATCH_TEST_MINUTES)?.let(Challenge::SnatchTest),
        armorBuildingBest(sessions, programs),
        bodyWeightKg?.takeIf { it > 0 }?.let { weight ->
            getUps.maxOfOrNull { it.load.kg }?.takeIf { it > 0 }?.let { Challenge.BodyweightGetUp(it, it / weight) }
        },
    )
}
