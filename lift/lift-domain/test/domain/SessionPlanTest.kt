package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPlanTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)
    private val squats = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5)
    private val presses = Block(BuiltInExercise.Press, rounds = 3, reps = 5)

    private fun logged(exercise: Exercise, times: Int, side: Side? = null) =
        (1..times).map { SetLog(it.toLong(), exercise, 24.0, 10, side, it.toLong()) }

    private fun day(vararg blocks: Block, plan: SessionPlan? = null) = ProgramDay(
        Program(ProgramKey.Custom("p"), listOf(blocks.toList()), daysPerWeek = 3, weeks = 4),
        number = 1,
        week = 1,
        blocks = blocks.toList(),
        plan = plan,
    )

    @Test
    fun `a day with no plan is as programmed`() {
        val day = day(swings, squats)
        assertEquals(SessionPlan.of(listOf(swings, squats)), day.today)
        assertEquals(8, day.plannedSets().size)
    }

    @Test
    fun `skipping a block drops its sets left and what comes next moves on`() {
        val done = logged(BuiltInExercise.TwoHandSwing, 2)
        val plan = SessionPlan.of(listOf(swings, squats)).skip(0, done = 2)
        val day = day(swings, squats, plan = plan)
        assertEquals(listOf(2, 3), day.blockSets().map { it.size })
        assertEquals(BuiltInExercise.GobletSquat, day.plannedSets()[nextPlanned(day.plannedSets(), done)!!].exercise)
        assertEquals(5, day(swings, squats, plan = plan.unskip(0)).blockSets()[0].size)
    }

    @Test
    fun `a change to a block plans its sets anew`() {
        val plan = SessionPlan.of(listOf(swings)).change(0) { it.copy(rounds = 3, kg = 28.0) }
        val sets = day(swings, plan = plan).plannedSets()
        assertEquals(3, sets.size)
        assertEquals(listOf(28.0), sets.map { it.kg }.distinct())
    }

    @Test
    fun `swapping before a set is done swaps the whole block`() {
        val plan = SessionPlan.of(listOf(swings)).swap(0, BuiltInExercise.GobletSquat, done = 0)
        assertEquals(BuiltInExercise.GobletSquat, plan.blocks.single().block.exercise)
        assertEquals(null, plan.blocks.single().before)
    }

    @Test
    fun `swapping part way keeps the sets done and gives the rounds left to the new exercise`() {
        // Press is one-sided: two of its six sets are its first round, done.
        val done = logged(BuiltInExercise.Press, 1, Side.Left) + logged(BuiltInExercise.Press, 1, Side.Right)
        val plan = SessionPlan.of(listOf(presses)).swap(0, BuiltInExercise.GobletSquat, done = 2)
        val sets = day(presses, plan = plan).plannedSets()
        assertEquals(
            listOf(
                BuiltInExercise.Press,
                BuiltInExercise.Press,
                BuiltInExercise.GobletSquat,
                BuiltInExercise.GobletSquat,
            ),
            sets.map { it.exercise },
        )
        assertEquals(2, nextPlanned(sets, done))
        // What is kept in the program is the block swapped in, every round of it.
        assertEquals(squats, plan.changedBlocks.single())
        assertEquals(1, plan.blocks.single().roundsDone(2))
    }

    @Test
    fun `a swapped exercise stays once a set of it is done`() {
        val plan = SessionPlan.of(listOf(presses)).swap(0, BuiltInExercise.GobletSquat, done = 2)
        assertTrue(plan.canSwap(0, done = 2))
        assertFalse(plan.canSwap(0, done = 3))
        assertEquals(plan, plan.swap(0, BuiltInExercise.Row, done = 3))
        assertEquals(BuiltInExercise.Row, plan.swap(0, BuiltInExercise.Row, done = 2).blocks.single().block.exercise)
    }

    @Test
    fun `a block skipped or done can take no other exercise`() {
        val plan = SessionPlan.of(listOf(swings))
        assertFalse(plan.canSwap(0, done = 5))
        assertFalse(plan.skip(0, done = 1).canSwap(0, done = 1))
    }

    @Test
    fun `planned sets are done by the sets logged of their lift and side`() {
        val planned = presses.plannedSets()
        val done = plannedDone(planned, logged(BuiltInExercise.Press, 2, Side.Right))
        assertEquals(listOf(false, true, false, true, false, false), done)
    }
}
