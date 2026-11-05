package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramDraftTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)

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
        val fromLadder = Block(BuiltInExercise.Press, rounds = 3, kind = BlockKind.Ladder, rungs = listOf(1, 2))
        assertEquals(ProgramDraft.NEW_REPS, fromLadder.asKind(BlockKind.Emom).reps)
    }

    @Test
    fun `a block keeps two bells only for a lift that can take them`() {
        val doubleClean = Block(BuiltInExercise.Clean, rounds = 5, reps = 5, double = true)
        assertEquals(false, doubleClean.withExercise(BuiltInExercise.GetUp).double)
        assertEquals(true, doubleClean.withExercise(BuiltInExercise.Press).double)
        assertEquals(true, swings.withExercise(BuiltInExercise.ArmorBuildingComplex).double)
    }

    @Test
    fun `a program made reads back as its draft`() {
        val program =
            Program(ProgramKey.Custom("a1"), listOf(listOf(swings)), daysPerWeek = 2, weeks = 6, name = "Mine")
        assertEquals(ProgramDraft("Mine", listOf(listOf(swings)), 2, 6), ProgramDraft.of(program))
    }

    @Test
    fun `a block keeps its load only while the equipment stays`() {
        val bell = swings.copy(load = Load.Bell(24.0))
        assertEquals(Load.Bell(24.0), bell.withExercise(BuiltInExercise.GobletSquat).load)
        val band = bell.withExercise(BuiltInExercise.BandPullApart)
        assertEquals(null, band.load)
        assertEquals(10, band.reps)
        val heavy = Load.Banded(Band.of(BandLevel.Heavy))
        assertEquals(heavy, band.withLoad(heavy).load)
        assertEquals(null, band.withLoad(heavy).withExercise(BuiltInExercise.PushUp).load)
    }

    @Test
    fun `a block's bell goes with it to two bells and back and a load of another kind is none`() {
        val cleans = Block(BuiltInExercise.Clean, rounds = 3, reps = 5, load = Load.Bell(16.0))
        val pair = cleans.withDouble(true)
        assertEquals(Load.Bell(16.0, double = true), pair.load)
        assertEquals(cleans, pair.withDouble(false))
        // With none named, two bells is still how it is done.
        assertEquals(null, cleans.copy(load = null).withDouble(true).load)
        assertEquals(null, cleans.withLoad(Load.Bar(60.0)).load)
        assertEquals(null, cleans.withLoad(null).load)
        assertEquals(Load.Bell(24.0, double = true), pair.withLoad(Load.Bell(24.0)).load)
    }

    @Test
    fun `a block becoming a hold runs as sets of thirty seconds`() {
        val emom = swings.copy(kind = BlockKind.Emom)
        val plank = emom.withExercise(BuiltInExercise.Plank)
        assertEquals(BlockKind.Sets, plank.kind)
        assertEquals(ProgramDraft.NEW_SECONDS, plank.reps)
        assertEquals(ProgramDraft.NEW_REPS, plank.withExercise(BuiltInExercise.PushUp).reps)
    }

    @Test
    fun `planned sets carry their block load`() {
        val sets = Block(BuiltInExercise.Press, rounds = 2, reps = 5, load = Load.Bell(16.0)).plannedSets()
        assertEquals(listOf<Load?>(Load.Bell(16.0)), sets.map { it.load }.distinct())
        assertEquals(4, sets.size)
    }
}
