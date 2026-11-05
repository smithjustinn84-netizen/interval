package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayProgressTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 10)
    private val squatEmom = Block(BuiltInExercise.GobletSquat, rounds = 4, reps = 5, kind = BlockKind.Emom)

    private fun logged(exercise: Exercise, times: Int) =
        (1..times).map { SetLog.of(it.toLong(), exercise, 24.0, 10, null, it.toLong()) }

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
        val medium = Band.of(BandLevel.Medium)
        val planned = PlannedSet(BuiltInExercise.Press, reps = 5, side = Side.Left, load = Load.Bell(20.0))
        assertEquals(Load.Bell(20.0), plannedLoad(planned, emptyList(), bar = null, emptyList(), 16.0, medium))
        val squat = PlannedSet(barbellLifts.first(), reps = 5, side = null)
        assertEquals(Load.Bar(15.0), plannedLoad(squat, emptyList(), bar = 15.0, emptyList(), 16.0, medium))
    }

    @Test
    fun `a lift with no load named takes the one in hand until it has a history`() {
        val medium = Band.of(BandLevel.Medium)
        val press = PlannedSet(BuiltInExercise.Press, reps = 5, side = Side.Left, double = true)
        assertEquals(Load.Bell(16.0, double = true), plannedLoad(press, emptyList(), null, emptyList(), 16.0, medium))
        val before = listOf(SetLog.of(1, BuiltInExercise.Press, 20.0, 5, Side.Left, 0, double = true))
        assertEquals(Load.Bell(20.0, double = true), plannedLoad(press, before, null, emptyList(), 16.0, medium))
        val pushUp = PlannedSet(BuiltInExercise.PushUp, reps = 10, side = null)
        assertEquals(Load.Bodyweight(0.0), plannedLoad(pushUp, emptyList(), null, emptyList(), 16.0, medium))
    }

    @Test
    fun `a planned band stands while it is offered and gives way to the last used once it is not`() {
        val offered = bands(emptyList())
        val (light, medium, heavy) = Triple(
            Band.of(BandLevel.Light),
            Band.of(BandLevel.Medium),
            Band.of(BandLevel.Heavy),
        )
        val pulls = PlannedSet(BuiltInExercise.BandPullApart, reps = 15, side = null, load = Load.Banded(heavy))
        assertEquals(Load.Banded(heavy), plannedLoad(pulls, emptyList(), null, offered, 16.0, medium))
        val before = listOf(SetLog.of(1, BuiltInExercise.BandPullApart, 0.0, 15, null, 0, band = light))
        val withoutHeavy = offered.filterNot { it.id == heavy.id }
        assertEquals(Load.Banded(light), plannedLoad(pulls, before, null, withoutHeavy, 16.0, medium))
        assertEquals(Load.Banded(medium), plannedLoad(pulls, emptyList(), null, withoutHeavy, 16.0, medium))
    }
}
