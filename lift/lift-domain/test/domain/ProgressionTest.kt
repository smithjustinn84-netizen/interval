package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionTest {
    private val bells = listOf(16.0, 24.0, 28.0)
    private val bands = bands(emptyList())

    private fun set(
        exercise: Exercise,
        kg: Double = 0.0,
        reps: Int = 10,
        side: Side? = null,
        seconds: Int? = null,
        band: Band? = null,
    ) = SetLog(0, exercise, kg, reps, side, 0, seconds = seconds, band = band)

    private fun sets(n: Int, make: () -> SetLog) = List(n) { make() }

    @Test
    fun `a kettlebell block done in full goes up a bell`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, kg = 16.0)
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 16.0) }
        assertEquals(goblets.copy(kg = 24.0), nextStep(goblets, done, bells, bands))
    }

    @Test
    fun `a barbell block done in full adds two and a half kilos to the bar`() {
        val squats = Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5, kg = 60.0)
        val done = sets(5) { set(BuiltInExercise.BarbellBackSquat, 60.0, reps = 5) }
        assertEquals(squats.copy(kg = 62.5), nextStep(squats, done, bells, bands))
        // With none named it goes up from the lightest done.
        val unnamed = squats.copy(kg = null)
        assertEquals(unnamed.copy(kg = 62.5), nextStep(unnamed, done, bells, bands))
    }

    @Test
    fun `a barbell block done under its weight earns nothing`() {
        val squats = Block(BuiltInExercise.BarbellBackSquat, rounds = 2, reps = 5, kg = 60.0)
        val done =
            listOf(
                set(BuiltInExercise.BarbellBackSquat, 60.0, reps = 5),
                set(BuiltInExercise.BarbellBackSquat, 57.5, reps = 5),
            )
        assertNull(nextStep(squats, done, bells, bands))
    }

    @Test
    fun `a block with no load named goes up from the lightest bell done`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10)
        val done = listOf(set(BuiltInExercise.GobletSquat, 24.0), set(BuiltInExercise.GobletSquat, 28.0))
        assertEquals(goblets.copy(kg = 28.0), nextStep(goblets, done, bells, bands))
    }

    @Test
    fun `the heaviest bell already done adds a rep`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10, kg = 28.0)
        val done = sets(2) { set(BuiltInExercise.GobletSquat, 28.0) }
        assertEquals(goblets.copy(reps = 11), nextStep(goblets, done, bells, bands))
    }

    @Test
    fun `a set missed or short or lighter earns nothing`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10, kg = 24.0)
        val full = set(BuiltInExercise.GobletSquat, 24.0)
        assertNull(nextStep(goblets, listOf(full, null), bells, bands))
        assertNull(nextStep(goblets, listOf(full, full.copy(reps = 9)), bells, bands))
        assertNull(nextStep(goblets, listOf(full, full.copy(bellKg = 16.0)), bells, bands))
    }

    @Test
    fun `bodyweight adds a rep and weight added goes up by its step`() {
        val pushUps = Block(BuiltInExercise.PushUp, rounds = 1, reps = 10)
        assertEquals(pushUps.copy(reps = 11), nextStep(pushUps, listOf(set(BuiltInExercise.PushUp)), bells, bands))
        val weighted = pushUps.copy(kg = 10.0)
        val done = listOf(set(BuiltInExercise.PushUp, 10.0))
        assertEquals(weighted.copy(kg = 10.0 + ADDED_KG_STEP), nextStep(weighted, done, bells, bands))
    }

    @Test
    fun `a band goes up a band and the heaviest adds a rep`() {
        val pulls = Block(BuiltInExercise.BandPullApart, rounds = 1, reps = 15, band = BandLevel.Medium.name)
        val medium = set(BuiltInExercise.BandPullApart, reps = 15, band = Band.of(BandLevel.Medium))
        assertEquals(pulls.copy(band = BandLevel.Heavy.name), nextStep(pulls, listOf(medium), bells, bands))
        val heaviest = pulls.copy(band = BandLevel.ExtraHeavy.name)
        val done = listOf(medium.copy(band = Band.of(BandLevel.ExtraHeavy)))
        assertEquals(heaviest.copy(reps = 16), nextStep(heaviest, done, bells, bands))
        // A lighter band than planned earns nothing.
        assertNull(nextStep(heaviest, listOf(medium), bells, bands))
    }

    @Test
    fun `a hold goes up five seconds and an EMOM a minute and a ladder not at all`() {
        val plank = Block(BuiltInExercise.Plank, rounds = 1, reps = 30)
        val held = listOf(set(BuiltInExercise.Plank, reps = 0, seconds = 30))
        assertEquals(plank.copy(reps = 35), nextStep(plank, held, bells, bands))
        assertNull(nextStep(plank, listOf(held.single().copy(seconds = 25)), bells, bands))

        val emom = Block(BuiltInExercise.TwoHandSwing, rounds = 2, reps = 10, kind = BlockKind.Emom, kg = 24.0)
        val swings = sets(2) { set(BuiltInExercise.TwoHandSwing, 24.0) }
        assertEquals(emom.copy(rounds = 3), nextStep(emom, swings, bells, bands))

        val ladder = Block(BuiltInExercise.TwoHandSwing, rounds = 1, kind = BlockKind.Ladder, rungs = listOf(1, 2))
        assertNull(nextStep(ladder, sets(2) { set(BuiltInExercise.TwoHandSwing, 24.0) }, bells, bands))
    }

    @Test
    fun `a day earns a step for each block done in full`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10, kg = 16.0)
        val rows = Block(BuiltInExercise.Row, rounds = 1, reps = 10, kg = 16.0)
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(goblets, rows)), daysPerWeek = 2, weeks = 2)
        val day = program.dayAfter(0)!!
        // The rows' right side was never done.
        val logged =
            sets(2) { set(BuiltInExercise.GobletSquat, 16.0) } + set(BuiltInExercise.Row, 16.0, side = Side.Left)
        assertEquals(listOf(BlockChange(0, goblets, goblets.copy(kg = 24.0))), progressions(day, logged, bells, bands))
    }

    @Test
    fun `a step taken is kept over the block as programmed`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10)
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(goblets)), daysPerWeek = 2, weeks = 2)
        val day = program.dayAfter(0)!!
        val edited = day.copy(plan = day.today.change(0) { it.copy(reps = 12) })
        val nudge = BlockChange(0, goblets.copy(reps = 12), goblets.copy(reps = 12, kg = 24.0))
        val changes = planChanges(edited, listOf(nudge))!!
        assertEquals(listOf(BlockChange(0, goblets, goblets.copy(reps = 12))), changes.withNudges(emptySet()).changes)
        assertEquals(listOf(BlockChange(0, goblets, nudge.to)), changes.withNudges(setOf(0)).changes)
    }

    @Test
    fun `a block building within a range adds a rep at the same bell`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, kg = 16.0, range = RepRange(10, 15))
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 16.0) }
        assertEquals(goblets.copy(reps = 11), nextStep(goblets, done, bells, bands))
        // One set short of where it stands earns nothing.
        assertNull(nextStep(goblets, done.dropLast(1) + set(BuiltInExercise.GobletSquat, 16.0, reps = 9), bells, bands))
    }

    @Test
    fun `the top of a range earns the next bell back at its bottom`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 15, kg = 16.0, range = RepRange(10, 15))
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 16.0, reps = 15) }
        assertEquals(goblets.copy(kg = 24.0, reps = 10), nextStep(goblets, done, bells, bands))
    }

    @Test
    fun `the top of a range with no heavier bell earns nothing`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 15, kg = 28.0, range = RepRange(10, 15))
        val done = sets(2) { set(BuiltInExercise.GobletSquat, 28.0, reps = 15) }
        assertNull(nextStep(goblets, done, bells, bands))
    }

    @Test
    fun `a barbell range adds to the bar at its top and starts again`() {
        val squats = Block(BuiltInExercise.BarbellBackSquat, rounds = 3, reps = 8, kg = 60.0, range = RepRange(5, 8))
        val done = sets(3) { set(BuiltInExercise.BarbellBackSquat, 60.0, reps = 8) }
        assertEquals(squats.copy(kg = 62.5, reps = 5), nextStep(squats, done, bells, bands))
    }

    @Test
    fun `an EMOM building within a range adds reps not minutes`() {
        val emom =
            Block(
                BuiltInExercise.Snatch,
                rounds = 10,
                reps = 5,
                kind = BlockKind.Emom,
                kg = 16.0,
                range = RepRange(5, 8),
            )
        val done = List(10) { set(BuiltInExercise.Snatch, 16.0, reps = 5, side = Side.entries[it % 2]) }
        assertEquals(emom.copy(reps = 6), nextStep(emom, done, bells, bands))
    }

    @Test
    fun `the top of a range on the heaviest bell is topped out and short of it is not`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 15, kg = 28.0, range = RepRange(10, 15))
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(goblets)), daysPerWeek = 1, weeks = 1)
        val day = program.dayAfter(0)!!
        val full = sets(2) { set(BuiltInExercise.GobletSquat, 28.0, reps = 15) }
        assertEquals(listOf(goblets), toppedOut(day, full, bells, bands))
        assertEquals(emptyList(), toppedOut(day, full.take(1), bells, bands))
        assertEquals(emptyList(), toppedOut(day, full, bells + 32.0, bands))
    }

    @Test
    fun `a band range done with a lighter band than planned is not topped out`() {
        val owned = bands(emptyList()).filterNot { it.archived }
        val (light, medium) = owned[0] to owned[1]
        val heaviest = owned.last()
        val pullAparts =
            Block(BuiltInExercise.BandPullApart, rounds = 1, reps = 15, band = medium.id, range = RepRange(10, 15))
        val day = Program(ProgramKey.Custom("p"), listOf(listOf(pullAparts)), daysPerWeek = 1, weeks = 1).dayAfter(0)!!
        assertEquals(
            emptyList(),
            toppedOut(day, listOf(set(BuiltInExercise.BandPullApart, reps = 15, band = light)), bells, owned),
        )
        val atHeaviest = pullAparts.copy(band = heaviest.id)
        val heavyDay = day.copy(blocks = listOf(atHeaviest))
        assertEquals(
            listOf(atHeaviest),
            toppedOut(heavyDay, listOf(set(BuiltInExercise.BandPullApart, reps = 15, band = heaviest)), bells, owned),
        )
    }
}
