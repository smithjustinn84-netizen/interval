package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a finished program came to, and the coach's call on the cycle after it. */
class ProgramRecapTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 10)
    private val squats = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5)
    private val mine =
        Program(ProgramKey.Custom("p"), listOf(listOf(swings, squats)), daysPerWeek = 2, weeks = 1, name = "Mine")
    private val gear = Gear(bells = listOf(16.0, 24.0))

    private fun set(exercise: Exercise, kg: Double, reps: Int, at: Long, warmUp: Boolean = false) =
        SetLog.of(0, exercise, kg, reps, null, at, warmUp = warmUp)

    /** Day [day] of [program] at [at], its planned sets each done at [kg], or only [sets] of them. */
    private fun day(
        program: Program,
        day: Int,
        at: Long,
        kg: Double = 16.0,
        sets: Int? = null,
        effort: PerceivedEffort? = null,
        extra: List<SetLog> = emptyList(),
    ): LiftSession {
        val planned = program.day(day).plannedSets()
        val logged = planned.take(sets ?: planned.size).mapIndexed { i, set ->
            set(set.exercise, kg, set.reps, at + i)
        }
        return LiftSession("s$at", at, at + 1_000, "", extra + logged, program.key, day = day, effort = effort)
    }

    /** Newest first, as the repository holds them. */
    private fun recap(program: Program, vararg sessions: LiftSession, daysBefore: Int = 0) = programRecap(
        programProgress(Followed(program, 0, daysBefore), sessions.sortedByDescending { it.startedAtEpochMillis }),
        gear,
        restSeconds = 60,
    )

    @Test
    fun `a program still going has no recap`() {
        assertNull(recap(mine, day(mine, 1, 100)))
    }

    @Test
    fun `a program done in full goes again from the loads last lifted`() {
        val recap = recap(mine, day(mine, 1, 100, kg = 16.0), day(mine, 2, 200, kg = 24.0))!!
        assertEquals(2, recap.sessions)
        assertEquals(0, recap.missed)
        assertEquals(1.0, recap.completion)
        assertEquals(NextCycle.GoAgain(fromLast = true), recap.next)
    }

    @Test
    fun `a program that names its loads goes again from them`() {
        val named = mine.copy(days = listOf(listOf(swings.withLoad(Load.Bell(24.0)), squats)))
        assertEquals(NextCycle.GoAgain(fromLast = false), recap(named, day(named, 1, 100), day(named, 2, 200))?.next)
    }

    @Test
    fun `each lift is shown as it started and ended from its best working set`() {
        val warmUp = set(BuiltInExercise.TwoHandSwing, 32.0, 5, 250, warmUp = true)
        val recap = recap(mine, day(mine, 1, 100, kg = 16.0), day(mine, 2, 200, kg = 24.0, extra = listOf(warmUp)))!!
        val swing = recap.lifts.first { it.exercise == BuiltInExercise.TwoHandSwing }
        assertEquals(16.0 to 24.0, swing.first.load.kg to swing.last.load.kg)
        assertEquals(listOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.GobletSquat), recap.lifts.map { it.exercise })
    }

    @Test
    fun `a lift done once and the days done before following show no change`() {
        val press = set(BuiltInExercise.Press, 16.0, 5, 150)
        val recap = recap(mine, day(mine, 2, 200, extra = listOf(press)), daysBefore = 1)!!
        assertEquals(1, recap.sessions)
        assertTrue(recap.lifts.isEmpty())
        assertEquals(1.0, recap.completion)
    }

    @Test
    fun `days missed and sets left ease the next cycle in`() {
        val long = mine.copy(weeks = 2)
        val recap = recap(long, day(long, 1, 100), day(long, 4, 400, sets = 2))!!
        assertEquals(2, recap.missed)
        assertTrue(recap.completion < EASE_OFF_BELOW)
        assertEquals(NextCycle.EaseOff(EaseReason.MissedWork), recap.next)
    }

    @Test
    fun `sessions that felt very hard ease the next cycle in`() {
        val recap = recap(
            mine,
            day(mine, 1, 100, effort = PerceivedEffort.AllOut),
            day(mine, 2, 200, effort = PerceivedEffort.VeryHard),
        )!!
        assertEquals(NextCycle.EaseOff(EaseReason.HardGoing), recap.next)
    }

    @Test
    fun `a beginner's program done well moves on to one that is no beginner's`() {
        val first = program(ProgramId.FirstHundredSwings)
        val sessions = (1..first.totalDays).map { day(first, it, it * 100L) }.toTypedArray()
        val next = assertIs<NextCycle.MoveOn>(recap(first, *sessions)?.next).program
        assertNotEquals(first.key, next.key)
        assertNotEquals(ProgramFamily.Beginner, (next.key as ProgramKey.BuiltIn).id.family)
        assertTrue(!next.retired && !next.barbell && next.supportedBy(gear.starterGear()))
    }

    @Test
    fun `a beginner's program done with days missed eases in rather than moving on`() {
        val first = program(ProgramId.FirstHundredSwings)
        val recap = recap(first, day(first, first.totalDays, 100), daysBefore = 0)!!
        assertIs<NextCycle.EaseOff>(recap.next)
    }

    @Test
    fun `going again a plan naming no load starts each lift at the load the run finished on`() {
        val run = listOf(day(mine, 1, 100, kg = 16.0), day(mine, 2, 200, kg = 24.0))
        val first = mine.day(1).plannedSets().first()
        val load = plannedLoad(first, run.setsOldestFirstOf(), null, emptyList(), 16.0, Band.Default, gear.bells)
        assertEquals(24.0, load.kg)
    }

    private fun List<LiftSession>.setsOldestFirstOf() = sortedByDescending { it.startedAtEpochMillis }.setsOldestFirst()
}
