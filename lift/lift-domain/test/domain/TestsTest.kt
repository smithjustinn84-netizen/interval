package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SECOND = 1_000L
private const val DAY = 86_400_000L

class TestsTest {
    private val snatch = strengthTest(TestId.SnatchTest)
    private val press = strengthTest(TestId.PressReps)
    private val plank = strengthTest(TestId.PlankHold)

    private fun set(exercise: Exercise, reps: Int, at: Long, kg: Double = 24.0, seconds: Int? = null) =
        SetLog.of(0, exercise, kg, reps, null, at, seconds = seconds)

    /** A finished session of [test] on [day]: its [sets] logged half a minute apart from the day's start. */
    private fun attempt(test: StrengthTest, day: Int, vararg reps: Int, kg: Double = 24.0) = LiftSession(
        "${test.id}-$day-$kg",
        day * DAY,
        day * DAY + reps.size * 30 * SECOND,
        "",
        reps.mapIndexed { index, count -> set(test.exercise, count, day * DAY + (index + 1) * 30 * SECOND, kg) },
        program = test.key,
        day = 1,
    )

    @Test
    fun `a test is a program of one day that is never offered`() {
        assertEquals(TestId.entries, strengthTests.map { it.id })
        strengthTests.forEach { test ->
            assertTrue(test.program.test)
            assertEquals(listOf(listOf(test.block)), test.program.days)
            assertEquals(test, testOf(test.key))
            assertEquals(test.key, ProgramKey.parse(test.key.stored))
        }
        assertTrue(builtInPrograms.none { it.test })
        assertTrue(programCatalogue().none { it.test })
        assertTrue(besideTheMat.map(::program).none { it.test })
        assertNull(testOf(ProgramKey.BuiltIn(ProgramId.SnatchTestPrep)))
        assertEquals(builtInPrograms + testPrograms, shippedPrograms)
    }

    @Test
    fun `a session of a test resolves to its day among the shipped programs`() {
        val session = attempt(snatch, 1, 10).copy(plan = SessionPlan.of(listOf(snatch.blockAt(Load.Bell(24.0)))))
        val day = shippedPrograms.dayOf(session)
        assertEquals(Load.Bell(24.0), day?.today?.blocks?.single()?.block?.load)
        assertEquals(SNATCH_TEST_MINUTES, day?.plannedSets()?.single()?.capMinutes)
    }

    @Test
    fun `a test against the clock counts every rep of its lift and the part round with them`() {
        val session = attempt(snatch, 1, 10, 10, 10, 10, 10, 10, 10, 10, 10, 7)
        assertEquals(
            listOf(Attempt(session.uid, session.finishedAtEpochMillis ?: 0, Load.Bell(24.0), 97)),
            attempts(snatch, listOf(session)),
        )
    }

    @Test
    fun `a strength test takes its best set and a hold its longest`() {
        assertEquals(9, attempts(press, listOf(attempt(press, 1, 8, 9))).single().result)
        val held = attempt(plank, 1).copy(
            sets = listOf(set(BuiltInExercise.Plank, 0, DAY, kg = 0.0, seconds = 95)),
        )
        assertEquals(95, attempts(plank, listOf(held)).single().result)
    }

    @Test
    fun `only finished sessions of the test that counted something are attempts`() {
        val free = attempt(snatch, 1, 10).copy(program = null, day = null)
        val under = attempt(snatch, 2, 10).copy(finishedAtEpochMillis = null)
        val other = attempt(snatch, 3, 10).copy(sets = listOf(set(BuiltInExercise.TwoHandSwing, 50, 3 * DAY)))
        assertEquals(emptyList(), attempts(snatch, listOf(free, under, other)))
    }

    @Test
    fun `attempts compare only at the same load`() {
        val sessions = listOf(attempt(snatch, 1, 40), attempt(snatch, 30, 60, kg = 16.0), attempt(snatch, 60, 45))
        val all = attempts(snatch, sessions)
        assertEquals(listOf(40, 60, 45), all.map { it.result })
        assertEquals(listOf(40, 45), all.at(Load.Bell(24.0)).map { it.result })
        val next = attempt(snatch, 90, 100)
        assertEquals(
            TestResult(TestId.SnatchTest, Load.Bell(24.0), 100, last = 45, target = 100),
            testResult(snatch, next.sets, sessions),
        )
        assertEquals(55, testResult(snatch, next.sets, sessions)?.change)
        assertTrue(testResult(snatch, next.sets, sessions)?.passed == true)
        val first = testResult(snatch, attempt(snatch, 90, 30, kg = 20.0).sets, sessions)
        assertNull(first?.last)
        assertNull(first?.change)
        assertFalse(first?.passed ?: true)
    }

    @Test
    fun `a retest is too soon within a week and due after four`() {
        val done = attempts(snatch, listOf(attempt(snatch, 10, 50)))
        val finished = done.single().atEpochMillis
        assertEquals(Retest.Never, retest(emptyList(), 0))
        assertEquals(Retest.TooSoon, retest(done, finished + 6 * DAY))
        assertEquals(Retest.Resting, retest(done, finished + 7 * DAY))
        assertEquals(Retest.Due, retest(done, finished + 28 * DAY))
    }

    @Test
    fun `a most reps test starts on the last result and a test never steps up`() {
        assertEquals(12, press.blockAt(Load.Bell(20.0), last = 12).reps)
        assertEquals(Load.Bell(20.0), press.blockAt(Load.Bell(20.0)).load)
        assertEquals(SNATCH_TEST_ROUND, snatch.blockAt(Load.Bell(24.0), last = 97).reps)
        // Three attempts, each better than the last: a program's density block would ask a rep more a round.
        val sessions =
            listOf(attempt(snatch, 1, 10, 10), attempt(snatch, 30, 10, 10, 10), attempt(snatch, 60, 10, 10, 10, 10))
        val today = attempt(snatch, 90, 10, 10, 10, 10, 10)
        val day = snatch.program.day(1)
        assertEquals(emptyList(), progressions(day, today.sets, listOf(24.0), emptyList(), before = sessions))
        assertEquals(emptyList(), toppedOut(day, today.sets, listOf(24.0), emptyList()))
    }

    @Test
    fun `the challenges that have a test name it`() {
        assertEquals(TestId.SnatchTest, Challenge.SnatchTest(50).test)
        assertEquals(TestId.ArmorBuilding, Challenge.ArmorBuilding(12).test)
        assertNull(Challenge.TenThousandSwings(1_200, ofProgram = false).test)
        assertNull(Challenge.BodyweightGetUp(24.0, 0.3).test)
    }
}
