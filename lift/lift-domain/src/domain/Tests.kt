package dev.justinsmith.lift.domain

// Tests: a benchmark done on purpose, on a fixed protocol at a known load, so two attempts can be set side by
// side. A test is a program of one day, never offered and never followed, so its session carries a plan and runs
// on the timers a program's day does; an attempt is a finished session of it. Nothing more is stored.

private const val MILLIS_PER_DAY = 86_400_000L

/** The days after an attempt within which another says little: the last one is still in the body. */
const val RETEST_TOO_SOON_DAYS = 7

/** The days after an attempt from which the next is due: long enough for training to have changed something. */
const val RETEST_DUE_DAYS = 28

/** The reps a round of the snatch test is logged as: ten a hand, as the test is usually paced. */
const val SNATCH_TEST_ROUND = 10

/** The longest a hold test's clock runs: the hold is stopped when it breaks, long before for most. */
const val HOLD_TEST_SECONDS = 300

/** The reps a most-reps test plans before any attempt: the log opens on them, to be counted up. */
const val FIRST_TEST_REPS = 5

/** The tests, by a stable id that is stored with their sessions. Screens name them from the app's strings. */
enum class TestId { SnatchTest, ArmorBuilding, PressReps, PushUpReps, PullUpReps, PlankHold }

/** What a test's result counts. */
enum class TestMeasure {
    /** Every rep of its lift within its clock's cap. */
    RepsInCap,

    /** The most reps in one set. */
    MostReps,

    /** The longest hold, in seconds. */
    LongestHold,
}

/**
 * A test: its one [block], what its result counts ([measure]) and the [target] it is done to pass, null for one
 * done only to beat the last.
 */
data class StrengthTest(val id: TestId, val block: Block, val measure: TestMeasure, val target: Int? = null) {
    val key: ProgramKey get() = ProgramKey.Test(id)

    val exercise: Exercise get() = block.exercise

    /** The minutes of its clock; null for a test with none. */
    val capMinutes: Int? get() = block.rounds.takeIf { measure == TestMeasure.RepsInCap }

    /** The one-day program a session of it is a day of. */
    val program: Program get() = Program(key, listOf(listOf(block)), daysPerWeek = 1, weeks = 1)

    /**
     * The block a session of it starts on: at [load], and for a most-reps test asking the [last] result again,
     * so the log opens on the number to beat.
     */
    fun blockAt(load: Load?, last: Int? = null): Block {
        val reps = last?.takeIf { measure == TestMeasure.MostReps && it > 0 } ?: block.reps
        return block.copy(reps = reps).withLoad(load ?: block.load)
    }
}

/** The tests, in the order the page lists them: the two against the clock, then strength, then the hold. */
val strengthTests: List<StrengthTest> = listOf(
    StrengthTest(
        TestId.SnatchTest,
        Block(BuiltInExercise.Snatch, SNATCH_TEST_MINUTES, SNATCH_TEST_ROUND, BlockKind.Density),
        TestMeasure.RepsInCap,
        target = SNATCH_TEST_REPS,
    ),
    StrengthTest(
        TestId.ArmorBuilding,
        Block(BuiltInExercise.ArmorBuildingComplex, ABC_MINUTES, reps = 1, kind = BlockKind.Density, double = true),
        TestMeasure.RepsInCap,
    ),
    StrengthTest(TestId.PressReps, Block(BuiltInExercise.Press, 1, FIRST_TEST_REPS), TestMeasure.MostReps),
    StrengthTest(TestId.PushUpReps, Block(BuiltInExercise.PushUp, 1, FIRST_TEST_REPS), TestMeasure.MostReps),
    StrengthTest(TestId.PullUpReps, Block(BuiltInExercise.PullUp, 1, FIRST_TEST_REPS), TestMeasure.MostReps),
    StrengthTest(TestId.PlankHold, Block(BuiltInExercise.Plank, 1, HOLD_TEST_SECONDS), TestMeasure.LongestHold),
)

/** The test of [id]. */
fun strengthTest(id: TestId): StrengthTest = strengthTests.first { it.id == id }

/** The test [key] is the program of; null for any other program's. */
fun testOf(key: ProgramKey?): StrengthTest? = (key as? ProgramKey.Test)?.let { strengthTest(it.id) }

/** The tests' programs: what a test's session is a day of. Never in the catalogue, and never followed. */
val testPrograms: List<Program> = strengthTests.map { it.program }

/** Every program this build ships: the built-ins and the tests' own. What a session's program is looked up among. */
val shippedPrograms: List<Program> = builtInPrograms + testPrograms

/** Whether this is a test's program: one day, done as an attempt, that never steps up and is never offered. */
val Program.test: Boolean get() = key is ProgramKey.Test

/** The test a challenge is the passive best of, and so the one its row starts; null for a challenge with none. */
val Challenge.test: TestId?
    get() = when (this) {
        is Challenge.SnatchTest -> TestId.SnatchTest
        is Challenge.ArmorBuilding -> TestId.ArmorBuilding
        is Challenge.TenThousandSwings, is Challenge.BodyweightGetUp -> null
    }

/** One attempt at a test: the session it was, when it finished, the [load] it was done at and what it came to. */
data class Attempt(val sessionUid: String, val atEpochMillis: Long, val load: Load, val result: Int)

/**
 * What [sets], one session's, come to as an attempt at [test]: every rep of its lift for a test against the
 * clock, else the best set, at the load of the set that made it (a clock's first). Null with nothing counted.
 */
fun testResult(test: StrengthTest, sets: List<SetLog>): Pair<Load, Int>? {
    val done = sets.filter { it.exercise.key == test.exercise.key }.sortedBy { it.loggedAtEpochMillis }
    return when (test.measure) {
        TestMeasure.RepsInCap ->
            done.filter { it.seconds == null }.takeIf { it.isNotEmpty() }?.let {
                it.first().load to
                    it.sumOf(SetLog::reps)
            }

        TestMeasure.MostReps -> done.filter { it.seconds == null }.maxByOrNull { it.reps }?.let { it.load to it.reps }

        TestMeasure.LongestHold -> done.filter { it.seconds != null }.maxByOrNull { it.seconds ?: 0 }
            ?.let { it.load to (it.seconds ?: 0) }
    }?.takeIf { it.second > 0 }
}

/** The attempts at [test] among [sessions], oldest first: each finished session of its program that counted. */
fun attempts(test: StrengthTest, sessions: List<LiftSession>): List<Attempt> =
    sessions.filter { it.program == test.key }
        .mapNotNull { session ->
            val finished = session.finishedAtEpochMillis ?: return@mapNotNull null
            testResult(test, session.sets)?.let { (load, result) -> Attempt(session.uid, finished, load, result) }
        }
        .sortedBy { it.atEpochMillis }

/** Those of these attempts done at [load]: the only ones a result at it compares with. */
fun List<Attempt>.at(load: Load): List<Attempt> = filter { it.load == load }

/** When a test is worth doing again. */
enum class Retest {
    /** Never done. */
    Never,

    /** Done within [RETEST_TOO_SOON_DAYS]: another now measures the last one's fatigue. */
    TooSoon,

    /** Done since, and not yet due. */
    Resting,

    /** [RETEST_DUE_DAYS] or more since the last. */
    Due,
}

/** Whether these attempts leave the test worth doing at [nowMillis], by the days since the last of them. */
fun retest(attempts: List<Attempt>, nowMillis: Long): Retest {
    val last = attempts.maxOfOrNull { it.atEpochMillis } ?: return Retest.Never
    val days = (nowMillis - last) / MILLIS_PER_DAY
    return when {
        days < RETEST_TOO_SOON_DAYS -> Retest.TooSoon
        days >= RETEST_DUE_DAYS -> Retest.Due
        else -> Retest.Resting
    }
}

/**
 * An attempt set against the ones before it: its [result] at [load], the [last] at that load (null for the first
 * there) and whether it reached the test's [target].
 */
data class TestResult(val test: TestId, val load: Load, val result: Int, val last: Int?, val target: Int?) {
    /** How far past the last at this load it came, negative for short of it; null for the first. */
    val change: Int? get() = last?.let { result - it }

    val passed: Boolean get() = target != null && result >= target
}

/**
 * What [sets], a session of [test] as it finishes, come to against the attempts in the sessions [before] it;
 * null with nothing counted.
 */
fun testResult(test: StrengthTest, sets: List<SetLog>, before: List<LiftSession>): TestResult? =
    testResult(test, sets)?.let { (load, result) ->
        TestResult(test.id, load, result, attempts(test, before).at(load).lastOrNull()?.result, test.target)
    }
