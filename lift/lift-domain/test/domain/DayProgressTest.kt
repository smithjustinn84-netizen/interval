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

    private val abcs = Block(
        BuiltInExercise.ArmorBuildingComplex,
        rounds = 20,
        reps = 1,
        kind = BlockKind.Density,
        double = true,
    )

    private fun round(at: Long) =
        SetLog.of(at, BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null, at, double = true)

    @Test
    fun `a density block plans its first round which opens it for its cap`() {
        assertEquals(1, abcs.plannedSets().size)
        val next = day(swings, abcs).nextSet(logged(BuiltInExercise.TwoHandSwing, 3))
        assertEquals(1, next?.block)
        assertEquals(20, next?.densityMinutes)
        assertNull(next?.emomMinutes)
    }

    @Test
    fun `rounds past the first within the cap are the density block's and never a later block's`() {
        val more = Block(BuiltInExercise.ArmorBuildingComplex, rounds = 2, reps = 1, double = true)
        val rounds = (0 until 12).map { round(100_000L + it * 90_000L) }
        val rows = day(abcs, more).planRows(rounds)
        assertEquals(1 to 1, rows[0].done to rows[0].total)
        // Twelve rounds inside twenty minutes leave the sets after it still to do.
        assertEquals(0, rows[1].done)
        assertEquals(1, day(abcs, more).nextSet(rounds)?.block)
        // A set of it done after the cap is the next block's.
        val after = round(100_000L + 21 * 60_000L)
        assertEquals(1, day(abcs, more).planRows(rounds + after)[1].done)
    }

    @Test
    fun `a one-sided density block takes its rounds on either side`() {
        val snatches = Block(BuiltInExercise.Snatch, rounds = 10, reps = 5, kind = BlockKind.Density)
        assertEquals(Side.Left, snatches.plannedSets().single().side)
        val rounds = (0 until 6).map {
            SetLog.of(it.toLong(), BuiltInExercise.Snatch, 16.0, 5, Side.entries[it % 2], 1_000L + it * 30_000L)
        }
        val matched = plannedRounds(snatches.plannedSets(), rounds)
        assertEquals(6, matched.single().size)
    }

    @Test
    fun `a one-sided density block begun on the right keeps its first round`() {
        val snatches = Block(BuiltInExercise.Snatch, rounds = 10, reps = 5, kind = BlockKind.Density)
        val after = Block(BuiltInExercise.Snatch, rounds = 1, reps = 5)
        val rounds = (0 until 6).map {
            SetLog.of(it.toLong(), BuiltInExercise.Snatch, 16.0, 5, Side.entries[(it + 1) % 2], 1_000L + it * 30_000L)
        }
        val rows = day(snatches, after).planRows(rounds)
        assertEquals(6, plannedRounds(snatches.plannedSets(), rounds).single().size)
        assertEquals(1 to 0, rows[0].done to rows[1].done)
    }

    @Test
    fun `a round tapped as the cap ends counts however quick the first round was`() {
        // An ABC is paced at well over five seconds, but the clock's cap began before this first round's log.
        val more = Block(BuiltInExercise.ArmorBuildingComplex, rounds = 1, reps = 1, double = true)
        val first = round(5_000L)
        val last = round(5_000L + 20 * 60_000L - 1_000L)
        val past = round(5_000L + 20 * 60_000L + 1_000L)
        assertEquals(2, plannedRounds(abcs.plannedSets(), listOf(first, last, past)).single().size)
        assertEquals(1, day(abcs, more).planRows(listOf(first, last, past))[1].done)
    }

    @Test
    fun `a density block's row says its rounds and any other's none`() {
        val rounds = (0 until 4).map { round(100_000L + it * 60_000L) }
        val rows = day(swings, abcs).planRows(logged(BuiltInExercise.TwoHandSwing, 3) + rounds)
        assertNull(rows[0].rounds)
        assertEquals(4, rows[1].rounds)
        assertEquals(0, day(abcs).planRows(emptyList()).single().rounds)
    }
}
