package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A grind's shortest rest: a block resting so steps to the next bell, however big the jump. */
private const val FLOOR = 60
private const val DAY = 86_400_000L

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
    ) = SetLog.of(0, exercise, kg, reps, side, 0, seconds = seconds, band = band)

    private fun sets(n: Int, make: () -> SetLog) = List(n) { make() }

    /** Every planned set of [block] done at [kg] a bell. */
    private fun doneFor(block: Block, kg: Double) = block.plannedSets().map {
        SetLog.of(0, it.exercise, kg, it.reps, it.side, 0, double = it.double)
    }

    /** [block], done in full at its bell, a step up, with [bells] and [pairs] owned and Lift's rest 90 s. */
    private fun climb(block: Block, bells: List<Double> = this.bells, pairs: Set<Double> = emptySet()) =
        nextStep(block, doneFor(block, block.load?.kg ?: 0.0), bells, bands, pairs, rest = 90)

    @Test
    fun `a kettlebell block done in full goes up a bell a small jump away`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, load = Load.Bell(24.0))
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 24.0) }
        assertEquals(goblets.copy(load = Load.Bell(26.0)), nextStep(goblets, done, listOf(24.0, 26.0), bands))
    }

    @Test
    fun `a barbell block done in full adds two and a half kilos to the bar`() {
        val squats = Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5, load = Load.Bar(60.0))
        val done = sets(5) { set(BuiltInExercise.BarbellBackSquat, 60.0, reps = 5) }
        assertEquals(squats.copy(load = Load.Bar(62.5)), nextStep(squats, done, bells, bands))
        // With none named it goes up from the lightest done.
        val unnamed = squats.copy(load = null)
        assertEquals(unnamed.copy(load = Load.Bar(62.5)), nextStep(unnamed, done, bells, bands))
    }

    @Test
    fun `a barbell block done under its weight earns nothing`() {
        val squats = Block(BuiltInExercise.BarbellBackSquat, rounds = 2, reps = 5, load = Load.Bar(60.0))
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
        assertEquals(goblets.copy(load = Load.Bell(26.0)), nextStep(goblets, done, listOf(24.0, 26.0, 28.0), bands))
    }

    @Test
    fun `the heaviest bell already done with every rung used adds a rep`() {
        // Rows have no harder lift with one bell, and no pair is owned: at the floor, at five sets, a rep more.
        val rows = Block(BuiltInExercise.Row, rounds = 5, reps = 10, load = Load.Bell(28.0), rest = FLOOR)
        assertEquals(rows.copy(reps = 11), nextStep(rows, doneFor(rows, 28.0), bells, bands))
    }

    @Test
    fun `a set missed or short or lighter earns nothing`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10, load = Load.Bell(24.0))
        val full = set(BuiltInExercise.GobletSquat, 24.0)
        assertNull(nextStep(goblets, listOf(full, null), bells, bands))
        assertNull(nextStep(goblets, listOf(full, full.copy(reps = 9)), bells, bands))
        assertNull(nextStep(goblets, listOf(full, full.copy(load = Load.Bell(16.0))), bells, bands))
    }

    @Test
    fun `bodyweight adds a rep and weight added goes up by its step`() {
        val pushUps = Block(BuiltInExercise.PushUp, rounds = 1, reps = 10)
        assertEquals(pushUps.copy(reps = 11), nextStep(pushUps, listOf(set(BuiltInExercise.PushUp)), bells, bands))
        val weighted = pushUps.copy(load = Load.Bodyweight(10.0))
        val done = listOf(set(BuiltInExercise.PushUp, 10.0))
        assertEquals(
            weighted.copy(load = Load.Bodyweight(10.0 + ADDED_KG_STEP)),
            nextStep(weighted, done, bells, bands),
        )
    }

    @Test
    fun `a band goes up a band and the heaviest adds a rep`() {
        val pulls =
            Block(BuiltInExercise.BandPullApart, rounds = 1, reps = 15, load = Load.Banded(Band.of(BandLevel.Medium)))
        val medium = set(BuiltInExercise.BandPullApart, reps = 15, band = Band.of(BandLevel.Medium))
        assertEquals(
            pulls.copy(load = Load.Banded(Band.of(BandLevel.Heavy))),
            nextStep(pulls, listOf(medium), bells, bands),
        )
        val heaviest = pulls.copy(load = Load.Banded(Band.of(BandLevel.ExtraHeavy)))
        val done = listOf(medium.copy(load = Load.Banded(Band.of(BandLevel.ExtraHeavy))))
        assertEquals(heaviest.copy(reps = 16), nextStep(heaviest, done, bells, bands))
        // A lighter band than planned earns nothing.
        assertNull(nextStep(heaviest, listOf(medium), bells, bands))
    }

    @Test
    fun `a hold goes up five seconds and an EMOM of a bell lift a minute once its reps are at their most`() {
        val plank = Block(BuiltInExercise.Plank, rounds = 1, reps = 30)
        val held = listOf(set(BuiltInExercise.Plank, reps = 0, seconds = 30))
        assertEquals(plank.copy(reps = 35), nextStep(plank, held, bells, bands))
        assertNull(nextStep(plank, listOf(held.single().copy(seconds = 25)), bells, bands))

        val emom =
            Block(BuiltInExercise.TwoHandSwing, rounds = 2, reps = 10, kind = BlockKind.Emom, load = Load.Bell(24.0))
        val swings = sets(2) { set(BuiltInExercise.TwoHandSwing, 24.0) }
        assertEquals(emom.copy(rounds = 3, origin = emom), nextStep(emom, swings, bells, bands))
        // Fewer than ten a minute: a rep more a minute first.
        val eight = emom.copy(reps = 8)
        assertEquals(eight.copy(reps = 9, origin = eight), nextStep(eight, doneFor(eight, 24.0), bells, bands))
        // Half an hour of minutes: the next bell, as it started.
        val long = emom.copy(rounds = 30, origin = eight)
        assertEquals(eight.copy(load = Load.Bell(28.0)), nextStep(long, doneFor(long, 24.0), bells, bands))
        // An EMOM of no bell keeps adding minutes.
        val burpees = Block(BuiltInExercise.Burpee, rounds = 10, reps = 5, kind = BlockKind.Emom)
        assertEquals(burpees.copy(rounds = 11), nextStep(burpees, doneFor(burpees, 0.0), bells, bands))
    }

    @Test
    fun `a day earns a step for each block done in full`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10, load = Load.Bell(16.0))
        val rows = Block(BuiltInExercise.Row, rounds = 1, reps = 10, load = Load.Bell(16.0))
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(goblets, rows)), daysPerWeek = 2, weeks = 2)
        val day = program.dayAfter(0)!!
        // The rows' right side was never done.
        val logged =
            sets(2) { set(BuiltInExercise.GobletSquat, 16.0) } + set(BuiltInExercise.Row, 16.0, side = Side.Left)
        assertEquals(
            listOf(BlockChange(0, goblets, goblets.copy(load = Load.Bell(17.0)))),
            progressions(day, logged, listOf(16.0, 17.0), bands, rest = FLOOR),
        )
    }

    @Test
    fun `a step taken is kept over the block as programmed`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10)
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(goblets)), daysPerWeek = 2, weeks = 2)
        val day = program.dayAfter(0)!!
        val edited = day.copy(plan = day.today.change(0) { it.copy(reps = 12) })
        val nudge = BlockChange(0, goblets.copy(reps = 12), goblets.copy(reps = 12, load = Load.Bell(24.0)))
        val changes = planChanges(edited, listOf(nudge))!!
        assertEquals(listOf(BlockChange(0, goblets, goblets.copy(reps = 12))), changes.withNudges(emptySet()).changes)
        assertEquals(listOf(BlockChange(0, goblets, nudge.to)), changes.withNudges(setOf(0)).changes)
    }

    @Test
    fun `a block building within a range adds a rep at the same bell`() {
        val goblets =
            Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, load = Load.Bell(16.0), range = RepRange(10, 15))
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 16.0) }
        assertEquals(goblets.copy(reps = 11), nextStep(goblets, done, bells, bands))
        // One set short of where it stands earns nothing.
        assertNull(nextStep(goblets, done.dropLast(1) + set(BuiltInExercise.GobletSquat, 16.0, reps = 9), bells, bands))
    }

    @Test
    fun `the top of a range earns the next bell back at its bottom`() {
        val goblets =
            Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 15, load = Load.Bell(24.0), range = RepRange(10, 15))
        val done = sets(3) { set(BuiltInExercise.GobletSquat, 24.0, reps = 15) }
        assertEquals(
            goblets.copy(load = Load.Bell(26.0), reps = 10),
            nextStep(goblets, done, listOf(24.0, 26.0), bands),
        )
    }

    @Test
    fun `the top of a range with every rung used and no heavier bell earns nothing`() {
        val rows = Block(
            BuiltInExercise.Row,
            rounds = 5,
            reps = 15,
            load = Load.Bell(28.0),
            range = RepRange(10, 15),
            rest = FLOOR,
        )
        assertNull(nextStep(rows, doneFor(rows, 28.0), bells, bands))
        assertEquals(Rung.ToppedOut, rungOf(rows, doneFor(rows, 28.0), bells, bands))
    }

    @Test
    fun `a barbell range adds to the bar at its top and starts again`() {
        val squats =
            Block(BuiltInExercise.BarbellBackSquat, rounds = 3, reps = 8, load = Load.Bar(60.0), range = RepRange(5, 8))
        val done = sets(3) { set(BuiltInExercise.BarbellBackSquat, 60.0, reps = 8) }
        assertEquals(squats.copy(load = Load.Bar(62.5), reps = 5), nextStep(squats, done, bells, bands))
    }

    @Test
    fun `an EMOM building within a range adds reps not minutes`() {
        val emom =
            Block(
                BuiltInExercise.Snatch,
                rounds = 10,
                reps = 5,
                kind = BlockKind.Emom,
                load = Load.Bell(16.0),
                range = RepRange(5, 8),
            )
        val done = List(10) { set(BuiltInExercise.Snatch, 16.0, reps = 5, side = Side.entries[it % 2]) }
        assertEquals(emom.copy(reps = 6), nextStep(emom, done, bells, bands))
    }

    @Test
    fun `the top of a range on the heaviest bell with every rung used is topped out and short of it is not`() {
        val rows = Block(
            BuiltInExercise.Row,
            rounds = 5,
            reps = 15,
            load = Load.Bell(28.0),
            range = RepRange(10, 15),
            rest = FLOOR,
        )
        val program = Program(ProgramKey.Custom("p"), listOf(listOf(rows)), daysPerWeek = 1, weeks = 1)
        val day = program.dayAfter(0)!!
        val full = doneFor(rows, 28.0)
        assertEquals(listOf(rows), toppedOut(day, full, bells, bands))
        assertEquals(emptyList(), toppedOut(day, full.take(1), bells, bands))
        assertEquals(emptyList(), toppedOut(day, full, bells + 32.0, bands))
        // Short of the floor it still has a rung: rest shorter.
        val rested = rows.copy(rest = null)
        assertEquals(emptyList(), toppedOut(day.copy(blocks = listOf(rested)), full, bells, bands, rest = 90))
    }

    @Test
    fun `a band range done with a lighter band than planned is not topped out`() {
        val owned = bands(emptyList()).filterNot { it.archived }
        val (light, medium) = owned[0] to owned[1]
        val heaviest = owned.last()
        val pullAparts =
            Block(
                BuiltInExercise.BandPullApart,
                rounds = 1,
                reps = 15,
                load = Load.Banded(medium),
                range = RepRange(10, 15),
            )
        val day = Program(ProgramKey.Custom("p"), listOf(listOf(pullAparts)), daysPerWeek = 1, weeks = 1).dayAfter(0)!!
        assertEquals(
            emptyList(),
            toppedOut(day, listOf(set(BuiltInExercise.BandPullApart, reps = 15, band = light)), bells, owned),
        )
        val atHeaviest = pullAparts.copy(load = Load.Banded(heaviest))
        val heavyDay = day.copy(blocks = listOf(atHeaviest))
        assertEquals(
            listOf(atHeaviest),
            toppedOut(heavyDay, listOf(set(BuiltInExercise.BandPullApart, reps = 15, band = heaviest)), bells, owned),
        )
    }

    @Test
    fun `two bells step only to a weight owned two of once pairs are known`() {
        // Five sets of a grind, at the floor, with two bells: nothing left at this bell but the next.
        val fronts =
            Block(
                BuiltInExercise.FrontSquat,
                rounds = 5,
                reps = 5,
                double = true,
                load = Load.Bell(16.0, double = true),
            )
        val done = sets(5) { SetLog.of(0, BuiltInExercise.FrontSquat, 16.0, 5, null, 0, double = true) }
        // A pair of 28s and one 24: the step skips the 24.
        assertEquals(
            fronts.copy(load = Load.Bell(28.0, double = true)),
            nextStep(fronts, done, bells, bands, setOf(16.0, 28.0), rest = FLOOR),
        )
        // No heavier pair: a rep more instead of a bell owned once.
        assertEquals(fronts.copy(reps = 6), nextStep(fronts, done, bells, bands, setOf(16.0), rest = FLOOR))
        // Pairs never said: any bell, as before.
        assertEquals(
            fronts.copy(load = Load.Bell(24.0, double = true)),
            nextStep(fronts, done, bells, bands, rest = FLOOR),
        )
        // One bell is not held back by the pairs.
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 1, reps = 10, load = Load.Bell(16.0))
        val one = listOf(set(BuiltInExercise.GobletSquat, 16.0))
        assertEquals(
            goblets.copy(load = Load.Bell(17.0)),
            nextStep(goblets, one, listOf(16.0, 17.0), bands, setOf(16.0)),
        )
    }

    @Test
    fun `a big jump to the next bell rests less then does more then a harder lift then the bell as it started`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, load = Load.Bell(16.0))
        // 16 to 24 is half again: 90 s of rest becomes 75, then 60, the shortest a grind rests.
        val at75 = goblets.copy(rest = 75, origin = goblets)
        assertEquals(at75, climb(goblets))
        val at60 = goblets.copy(rest = 60, origin = goblets)
        assertEquals(at60, climb(at75))
        // Then a set more, up to five.
        assertEquals(at60.copy(rounds = 4), climb(at60))
        val five = at60.copy(rounds = 5)
        // Then front squats at the same bell, as the goblets started.
        val fronts = goblets.copy(exercise = BuiltInExercise.FrontSquat, origin = goblets)
        assertEquals(fronts, climb(five))
        // Which climb in turn, then go back to goblets at 24 kg, as they started, with Lift's rest.
        assertEquals(fronts.copy(rest = 75), climb(fronts))
        val frontsDone = fronts.copy(rest = 60, rounds = 5)
        assertEquals(goblets.copy(load = Load.Bell(24.0)), climb(frontsDone))
    }

    @Test
    fun `a medium jump rests less and does more but takes no harder lift`() {
        // 24 to 28 is a sixth; 20 to 24 is a fifth, still medium.
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, load = Load.Bell(24.0))
        assertEquals(goblets.copy(rest = 75, origin = goblets), climb(goblets))
        val done = goblets.copy(rest = 60, rounds = 5, origin = goblets)
        assertEquals(goblets.copy(load = Load.Bell(28.0)), climb(done))
        // With no origin kept, it goes on as it stands, at Lift's rest.
        val at20 = goblets.copy(load = Load.Bell(20.0), rest = 60, rounds = 5)
        assertEquals(at20.copy(load = Load.Bell(24.0), rest = null), climb(at20, listOf(20.0, 24.0)))
        // 16 to 18 is an eighth.
        val at16 = goblets.copy(load = Load.Bell(16.0), rest = 60, rounds = 5)
        assertEquals(at16.copy(load = Load.Bell(18.0), rest = null), climb(at16, listOf(16.0, 18.0)))
        // 16 to 20 is a quarter: big.
        assertEquals(BuiltInExercise.FrontSquat, climb(at16, listOf(16.0, 20.0))?.exercise)
    }

    @Test
    fun `a thrown lift rests down to thirty seconds and adds sets up to ten`() {
        val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 15, load = Load.Bell(16.0), rest = 45)
        assertEquals(swings.copy(rest = 30, origin = swings), climb(swings))
        val atFloor = swings.copy(rest = 30, rounds = 9)
        assertEquals(atFloor.copy(rounds = 10, origin = atFloor), climb(atFloor))
        // At ten, one hand at the same bell.
        val one = climb(atFloor.copy(rounds = 10))!!
        assertEquals(BuiltInExercise.OneHandSwing, one.exercise)
        assertEquals(Load.Bell(16.0), one.load)
        assertNull(one.rest)
    }

    @Test
    fun `with no heavier bell a harder lift comes before the top`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 5, reps = 10, load = Load.Bell(28.0), rest = FLOOR)
        assertEquals(goblets.copy(exercise = BuiltInExercise.FrontSquat, rest = null, origin = goblets), climb(goblets))
        // On the harder lift with every rung used, there is nowhere further: a rep more, as sets with no range take.
        val fronts = goblets.copy(exercise = BuiltInExercise.FrontSquat, origin = goblets)
        assertEquals(fronts.copy(reps = 11), climb(fronts))
    }

    @Test
    fun `one bell goes to two at the same bell only with a pair of it owned`() {
        val rows = Block(BuiltInExercise.Row, rounds = 5, reps = 8, load = Load.Bell(16.0), rest = FLOOR)
        val doubles = rows.copy(double = true, rest = null, load = Load.Bell(16.0, double = true), origin = rows)
        assertEquals(doubles, climb(rows, pairs = setOf(16.0)))
        // With no pairs said, an owned bell isn't taken to be two: the bell instead.
        assertEquals(rows.copy(load = Load.Bell(24.0), rest = null), climb(rows))
        // Back from two bells to one, at the next.
        val doublesDone = doubles.copy(rounds = 5, rest = FLOOR)
        assertEquals(rows.copy(load = Load.Bell(24.0), rest = null), climb(doublesDone, pairs = setOf(16.0)))
    }

    @Test
    fun `a ladder adds ladders then climbing from one a rung`() {
        val presses =
            Block(
                BuiltInExercise.Press,
                rounds = 3,
                kind = BlockKind.Ladder,
                rungs = listOf(1, 2, 3),
                load = Load.Bell(16.0),
            )
        assertEquals(presses.copy(rounds = 4, origin = presses), climb(presses))
        val five = presses.copy(rounds = 5, origin = presses)
        assertEquals(five.copy(rungs = listOf(1, 2, 3, 4)), climb(five))
        val top = five.copy(rungs = (1..5).toList())
        assertEquals(presses.copy(load = Load.Bell(24.0)), climb(top, pairs = emptySet()))
        // Rungs that don't climb from one only add ladders, and take no harder lift: 10,000 swings go on two-handed.
        val swings = Block(
            BuiltInExercise.TwoHandSwing,
            rounds = 5,
            kind = BlockKind.Ladder,
            rungs = listOf(10, 15, 25, 50),
            load = Load.Bell(16.0),
        )
        assertEquals(swings.copy(load = Load.Bell(24.0)), climb(swings))
        // A rung short earns nothing.
        val short = doneFor(presses, 16.0).mapIndexed { i, set -> if (i == 5) set.copy(reps = 2) else set }
        assertNull(nextStep(presses, short, bells, bands))
    }

    @Test
    fun `a step says what kind it is`() {
        val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10, load = Load.Bell(16.0))
        val five = goblets.copy(rest = 60, rounds = 5, origin = goblets)
        val fronts = climb(five)!!
        assertEquals(StepKind.Denser, stepKind(goblets, climb(goblets)!!))
        assertEquals(StepKind.Volume, stepKind(goblets.copy(rest = 60, origin = goblets), five.copy(rounds = 4)))
        assertEquals(StepKind.Variation, stepKind(five, fronts))
        assertEquals(StepKind.Bell, stepKind(fronts, goblets.copy(load = Load.Bell(24.0))))
        assertEquals(StepKind.Reps, stepKind(goblets, goblets.copy(reps = 11)))
    }

    private val abcs = Block(
        BuiltInExercise.ArmorBuildingComplex,
        rounds = 20,
        reps = 1,
        kind = BlockKind.Density,
        double = true,
    )
    private val abcProgram = Program(ProgramKey.Custom("abc"), listOf(listOf(abcs)), daysPerWeek = 3, weeks = 4)

    private fun abcRounds(n: Int, from: Long = 0) = List(n) {
        SetLog.of(0, BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null, from + 30_000L + it * 80_000L, double = true)
    }

    private fun abcSession(n: Int, at: Long) =
        LiftSession("s$at", at, at + 1_500_000, "", abcRounds(n, at), program = abcProgram.key, day = 1)

    @Test
    fun `a density block counts its rounds across the sessions of it`() {
        val before = listOf(abcSession(12, 2 * DAY), abcSession(10, DAY))
        assertEquals(listOf(10, 12), densityRounds(abcs, abcProgram, before))
        assertEquals(12, densityBest(abcs, abcProgram, before))
        // One of another cap is another block.
        assertEquals(emptyList(), densityRounds(abcs.copy(rounds = 15), abcProgram, before))
    }

    @Test
    fun `two sessions that beat the best by a round add a rep to a round and the bell waits`() {
        val day = abcProgram.day(1)
        val before = listOf(abcSession(10, DAY), abcSession(11, 2 * DAY))
        val change = progressions(day, abcRounds(11, 3 * DAY), bells, bands, before = before).single()
        assertEquals(abcs.copy(reps = 2), change.to)
        // One that only matched the best earns nothing, nor do two sessions with no best before them.
        assertEquals(emptyList(), progressions(day, abcRounds(10, 3 * DAY), bells, bands, before = before))
        assertEquals(emptyList(), progressions(day, abcRounds(12, 3 * DAY), bells, bands, before = before.drop(1)))
    }

    @Test
    fun `a density block never steps by the EMOM's minute or the load`() {
        assertNull(nextStep(abcs, abcRounds(1), bells, bands))
        assertNull(densityStep(abcs, listOf(null), listOf(8, 10, 10)))
    }

    @Test
    fun `a density block done says its rounds against the best before it`() {
        val day = abcProgram.day(1)
        val before = listOf(abcSession(10, DAY), abcSession(14, 2 * DAY))
        assertEquals(listOf(DensityDone(abcs, 15, 14)), densityDone(day, abcRounds(15, 3 * DAY), before))
        assertTrue(densityDone(day, abcRounds(15, 3 * DAY), before).single().newBest)
        assertFalse(densityDone(day, abcRounds(14, 3 * DAY), before).single().newBest)
        // The first time sets the best rather than beating one, and a block not begun says nothing.
        assertFalse(densityDone(day, abcRounds(9, 3 * DAY), emptyList()).single().newBest)
        assertEquals(emptyList(), densityDone(day, emptyList(), before))
    }
}
