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

    private val medium = Band.of(BandLevel.Medium)

    private fun loadFor(
        set: PlannedSet,
        history: List<SetLog>,
        bar: Double?,
        offered: List<Band>,
        owned: List<Double> = emptyList(),
    ) = plannedLoad(set, history, bar, offered, bellKg = 16.0, band = medium, owned = owned)

    @Test
    fun `a planned load wins and a bar lift with no history starts at the user's bar`() {
        val planned = PlannedSet(BuiltInExercise.Press, reps = 5, side = Side.Left, load = Load.Bell(20.0))
        assertEquals(Load.Bell(20.0), loadFor(planned, emptyList(), null, emptyList()))
        val squat = PlannedSet(barbellLifts.first(), reps = 5, side = null)
        assertEquals(Load.Bar(15.0), loadFor(squat, emptyList(), 15.0, emptyList()))
    }

    @Test
    fun `a lift held overhead starts at the lightest bell owned until it has a history`() {
        val owned = listOf(12.0, 16.0, 24.0)
        val overhead = listOf(
            BuiltInExercise.Press,
            BuiltInExercise.BottomsUpPress,
            BuiltInExercise.GetUp,
            BuiltInExercise.Windmill,
            BuiltInExercise.Halo,
            BuiltInExercise.BottomsUpCarry,
        )
        overhead.forEach { lift ->
            val set = PlannedSet(lift, reps = 3, side = null)
            assertEquals(12.0, loadFor(set, emptyList(), null, emptyList(), owned).kg, "$lift")
            // With no bells said, a light one; and never above the bell in hand.
            assertEquals(LIGHT_BELL_KG, loadFor(set, emptyList(), null, emptyList()).kg, "$lift")
            assertEquals(16.0, loadFor(set, emptyList(), null, emptyList(), owned = listOf(20.0, 24.0)).kg, "$lift")
        }
        val pressed = listOf(SetLog.of(1, BuiltInExercise.Press, 20.0, 5, Side.Left, 0))
        val press = PlannedSet(BuiltInExercise.Press, reps = 5, side = Side.Left)
        assertEquals(20.0, loadFor(press, pressed, null, emptyList(), owned).kg)
        // What is swung, squatted, rowed or carried at the side takes the bell in hand as before.
        listOf(
            BuiltInExercise.TwoHandSwing,
            BuiltInExercise.GobletSquat,
            BuiltInExercise.Row,
            BuiltInExercise.SuitcaseCarry,
        )
            .forEach { lift ->
                assertEquals(
                    16.0,
                    loadFor(PlannedSet(lift, reps = 5, side = null), emptyList(), null, emptyList(), owned).kg,
                )
            }
    }

    @Test
    fun `a lift with no load named takes the one in hand until it has a history`() {
        val squat = PlannedSet(BuiltInExercise.FrontSquat, reps = 5, side = null, double = true)
        assertEquals(Load.Bell(16.0, double = true), loadFor(squat, emptyList(), null, emptyList()))
        val before = listOf(SetLog.of(1, BuiltInExercise.FrontSquat, 20.0, 5, null, 0, double = true))
        assertEquals(Load.Bell(20.0, double = true), loadFor(squat, before, null, emptyList()))
        val pushUp = PlannedSet(BuiltInExercise.PushUp, reps = 10, side = null)
        assertEquals(Load.Bodyweight(0.0), loadFor(pushUp, emptyList(), null, emptyList()))
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
        assertEquals(Load.Banded(heavy), loadFor(pulls, emptyList(), null, offered))
        val before = listOf(SetLog.of(1, BuiltInExercise.BandPullApart, 0.0, 15, null, 0, band = light))
        val withoutHeavy = offered.filterNot { it.id == heavy.id }
        assertEquals(Load.Banded(light), loadFor(pulls, before, null, withoutHeavy))
        assertEquals(Load.Banded(medium), loadFor(pulls, emptyList(), null, withoutHeavy))
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
