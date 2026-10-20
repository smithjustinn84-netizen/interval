package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgramsTest {
    private fun logged(exercise: Exercise, side: Side? = null) = SetLog(0, exercise, 24.0, 10, side, 0)

    @Test
    fun `a one-sided block is done on both sides and an EMOM alternates by minute`() {
        assertEquals(10, Block(Exercise.GetUp, rounds = 5, reps = 1).plannedSets().size)
        val emom = Block(Exercise.Clean, rounds = 4, reps = 3, kind = BlockKind.Emom).plannedSets()
        assertEquals(listOf(Side.Left, Side.Right, Side.Left, Side.Right), emom.map { it.side })
    }

    @Test
    fun `a ladder climbs its rungs on each side`() {
        val ladder = Block(Exercise.Press, rounds = 2, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3))
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 1, 1, 2, 2, 3, 3), ladder.plannedSets().map { it.reps })
        assertEquals(24, ladder.totalReps)
    }

    @Test
    fun `the daily program is 100 swings and 10 get-ups`() {
        val day = assertNotNullDay(program(ProgramId.DailySwingsGetUps).dayAfter(0))
        assertEquals(100, day.blocks[0].totalReps)
        assertEquals(10, day.blocks[1].totalReps)
    }

    @Test
    fun `days follow in turn and the program ends after its weeks`() {
        val ladders = program(ProgramId.PressLadders)
        assertEquals(ladders.days[1], ladders.dayAfter(1)?.blocks)
        assertEquals(2, ladders.dayAfter(3)?.week)
        assertEquals(ladders.days[0], ladders.dayAfter(3)?.blocks)
        assertNull(ladders.dayAfter(ladders.totalDays))
    }

    @Test
    fun `the next planned set skips what has been logged by lift and side`() {
        val planned = Block(Exercise.GetUp, rounds = 2, reps = 1).plannedSets() // L R L R
        assertEquals(0, nextPlanned(planned, emptyList()))
        assertEquals(1, nextPlanned(planned, listOf(logged(Exercise.GetUp, Side.Left))))
        assertEquals(
            3,
            nextPlanned(
                planned,
                listOf(
                    logged(Exercise.GetUp, Side.Left),
                    logged(Exercise.GetUp, Side.Right),
                    logged(Exercise.GetUp, Side.Left),
                ),
            ),
        )
        assertNull(
            nextPlanned(
                planned,
                List(2) { logged(Exercise.GetUp, Side.Left) } + List(2) { logged(Exercise.GetUp, Side.Right) },
            ),
        )
    }

    @Test
    fun `the suggested bell is the last used for the lift`() {
        val history =
            listOf(SetLog(0, Exercise.Press, 16.0, 5, Side.Left, 0), SetLog(0, Exercise.Press, 20.0, 3, Side.Left, 1))
        assertEquals(20.0, suggestedBell(Exercise.Press, history, fallback = 12.0))
        assertEquals(12.0, suggestedBell(Exercise.Snatch, history, fallback = 12.0))
    }

    private fun assertNotNullDay(day: ProgramDay?): ProgramDay = kotlin.test.assertNotNull(day)
}
