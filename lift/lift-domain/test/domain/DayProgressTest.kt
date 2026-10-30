package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayProgressTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 10)
    private val squatEmom = Block(BuiltInExercise.GobletSquat, rounds = 4, reps = 5, kind = BlockKind.Emom)

    private fun logged(exercise: Exercise, times: Int) =
        (1..times).map { SetLog(it.toLong(), exercise, 24.0, 10, null, it.toLong()) }

    private fun day(vararg blocks: Block, plan: SessionPlan? = null) = ProgramDay(
        Program(ProgramKey.Custom("p"), listOf(blocks.toList()), daysPerWeek = 3, weeks = 4),
        number = 1,
        week = 1,
        blocks = blocks.toList(),
        plan = plan,
    )

    @Test
    fun `the next set is the first not done and counts from the start of the day`() {
        val next = day(swings, squatEmom).nextSet(logged(BuiltInExercise.TwoHandSwing, 2))
        assertEquals(BuiltInExercise.TwoHandSwing, next?.set?.exercise)
        assertEquals(2, next?.index)
        assertEquals(7, next?.total)
        assertEquals(0, next?.block)
        assertNull(next?.emomMinutes)
    }

    @Test
    fun `the first minute of an EMOM opens it for all its minutes`() {
        val next = day(swings, squatEmom).nextSet(logged(BuiltInExercise.TwoHandSwing, 3))
        assertEquals(1, next?.block)
        assertEquals(4, next?.emomMinutes)
    }

    @Test
    fun `a minute past the first of an EMOM opens nothing`() {
        val done = logged(BuiltInExercise.TwoHandSwing, 3) + logged(BuiltInExercise.GobletSquat, 1)
        assertNull(day(swings, squatEmom).nextSet(done)?.emomMinutes)
    }

    @Test
    fun `nothing is next once every set is done`() {
        val done = logged(BuiltInExercise.TwoHandSwing, 3) + logged(BuiltInExercise.GobletSquat, 4)
        assertNull(day(swings, squatEmom).nextSet(done))
    }

    @Test
    fun `plan rows count each block's sets done and mark one the session changed`() {
        val plan = SessionPlan.of(listOf(swings, squatEmom)).change(1) { it.copy(rounds = 2) }
        val rows = day(swings, squatEmom, plan = plan).planRows(logged(BuiltInExercise.TwoHandSwing, 3))
        assertEquals(listOf(3 to 3, 0 to 2), rows.map { it.done to it.total })
        assertEquals(listOf(false, true), rows.map { it.changed })
        assertEquals(listOf(false, true), rows.map { it.open })
    }

    @Test
    fun `a planned load wins and a bar lift with no history starts at the user's bar`() {
        val planned = PlannedSet(BuiltInExercise.Press, reps = 5, side = Side.Left, kg = 20.0)
        assertEquals(20.0, suggestedLoad(planned, emptyList(), bar = null, bellKg = 16.0))
        val squat = PlannedSet(barbellLifts.first(), reps = 5, side = null)
        assertEquals(15.0, suggestedLoad(squat, emptyList(), bar = 15.0, bellKg = 16.0))
        assertNull(plannedBand(planned, emptyList(), emptyList(), Band.of(BandLevel.Medium)))
    }
}
