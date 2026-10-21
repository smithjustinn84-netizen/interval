package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramDraftTest {
    private val swings = Block(Exercise.TwoHandSwing, rounds = 5, reps = 10)

    @Test
    fun `a draft saves once named with a block on every day`() {
        assertFalse(ProgramDraft().canSave)
        assertFalse(ProgramDraft(name = "Swings").canSave)
        val oneDay = ProgramDraft(name = "Swings").addBlock(0)
        assertTrue(oneDay.canSave)
        assertFalse(oneDay.addDay().canSave)
        assertFalse(oneDay.copy(name = "  ").canSave)
    }

    @Test
    fun `days and blocks are added changed and removed in place`() {
        val draft = ProgramDraft().addBlock(0, swings).addDay().addBlock(1, swings)
            .changeBlock(1, 0) { it.copy(reps = 20) }
        assertEquals(listOf(listOf(swings), listOf(swings.copy(reps = 20))), draft.days)
        assertEquals(listOf(listOf(swings.copy(reps = 20))), draft.removeDay(0).days)
        assertEquals(listOf(emptyList(), listOf(swings.copy(reps = 20))), draft.removeBlock(0, 0).days)
        // The last day stays.
        assertEquals(1, ProgramDraft().removeDay(0).days.size)
        assertEquals(ProgramDraft.MAX_DAYS, (1..10).fold(ProgramDraft()) { d, _ -> d.addDay() }.days.size)
    }

    @Test
    fun `a block run as a ladder gets rungs and back as sets keeps its reps`() {
        val ladder = swings.asKind(BlockKind.Ladder)
        assertEquals(listOf(1, 2, 3), ladder.rungs)
        assertEquals(swings.copy(kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)), ladder)
        val fromLadder = Block(Exercise.Press, rounds = 3, kind = BlockKind.Ladder, rungs = listOf(1, 2))
        assertEquals(ProgramDraft.NEW_REPS, fromLadder.asKind(BlockKind.Emom).reps)
    }

    @Test
    fun `a block keeps two bells only for a lift that can take them`() {
        val doubleClean = Block(Exercise.Clean, rounds = 5, reps = 5, double = true)
        assertEquals(false, doubleClean.withExercise(Exercise.GetUp).double)
        assertEquals(true, doubleClean.withExercise(Exercise.Press).double)
        assertEquals(true, swings.withExercise(Exercise.ArmorBuildingComplex).double)
    }

    @Test
    fun `a program made reads back as its draft`() {
        val program =
            Program(ProgramKey.Custom("a1"), listOf(listOf(swings)), daysPerWeek = 2, weeks = 6, name = "Mine")
        assertEquals(ProgramDraft("Mine", listOf(listOf(swings)), 2, 6), ProgramDraft.of(program))
    }
}
