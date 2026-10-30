package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramsTest {
    private fun logged(exercise: Exercise, side: Side? = null) = SetLog(0, exercise, 24.0, 10, side, 0)

    @Test
    fun `a one-sided block is done on both sides and an EMOM alternates by minute`() {
        assertEquals(10, Block(BuiltInExercise.GetUp, rounds = 5, reps = 1).plannedSets().size)
        val emom = Block(BuiltInExercise.Clean, rounds = 4, reps = 3, kind = BlockKind.Emom).plannedSets()
        assertEquals(listOf(Side.Left, Side.Right, Side.Left, Side.Right), emom.map { it.side })
    }

    @Test
    fun `a ladder climbs its rungs on each side`() {
        val ladder = Block(BuiltInExercise.Press, rounds = 2, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3))
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
    fun `a day past the program's weeks still lays out`() {
        val goblets = program(ProgramId.GobletSquatRow)
        val day = goblets.day(goblets.totalDays + 1)
        assertEquals(goblets.days[0], day.blocks)
        assertEquals(goblets.weeks + 1, day.week)
        assertEquals(goblets.dayAfter(2), goblets.day(3))
    }

    @Test
    fun `the next planned set skips what has been logged by lift and side`() {
        val planned = Block(BuiltInExercise.GetUp, rounds = 2, reps = 1).plannedSets() // L R L R
        assertEquals(0, nextPlanned(planned, emptyList()))
        assertEquals(1, nextPlanned(planned, listOf(logged(BuiltInExercise.GetUp, Side.Left))))
        assertEquals(
            3,
            nextPlanned(
                planned,
                listOf(
                    logged(BuiltInExercise.GetUp, Side.Left),
                    logged(BuiltInExercise.GetUp, Side.Right),
                    logged(BuiltInExercise.GetUp, Side.Left),
                ),
            ),
        )
        assertNull(
            nextPlanned(
                planned,
                List(2) { logged(BuiltInExercise.GetUp, Side.Left) } +
                    List(2) { logged(BuiltInExercise.GetUp, Side.Right) },
            ),
        )
    }

    @Test
    fun `the suggested bell is the last used for the lift`() {
        val history =
            listOf(
                SetLog(0, BuiltInExercise.Press, 16.0, 5, Side.Left, 0),
                SetLog(0, BuiltInExercise.Press, 20.0, 3, Side.Left, 1),
            )
        assertEquals(20.0, suggestedBell(BuiltInExercise.Press, false, history, fallback = 12.0))
        assertEquals(12.0, suggestedBell(BuiltInExercise.Snatch, false, history, fallback = 12.0))
    }

    @Test
    fun `the suggested bell for two bells is the last pair used else the last single`() {
        val single = SetLog(0, BuiltInExercise.Press, 20.0, 3, Side.Left, 0)
        val pair = SetLog(0, BuiltInExercise.Press, 16.0, 5, null, 1, double = true)
        assertEquals(20.0, suggestedBell(BuiltInExercise.Press, true, listOf(single), fallback = 12.0))
        assertEquals(16.0, suggestedBell(BuiltInExercise.Press, true, listOf(pair, single), fallback = 12.0))
        assertEquals(20.0, suggestedBell(BuiltInExercise.Press, false, listOf(single, pair), fallback = 12.0))
    }

    @Test
    fun `a block with two bells plans sets with no side and its EMOM does not alternate`() {
        val sets = Block(BuiltInExercise.Clean, rounds = 3, reps = 5, double = true).plannedSets()
        assertEquals(List(3) { PlannedSet(BuiltInExercise.Clean, 5, null, double = true) }, sets)
        val emom = Block(
            BuiltInExercise.Press,
            rounds = 4,
            reps = 3,
            kind = BlockKind.Emom,
            double = true,
        ).plannedSets()
        assertEquals(List(4) { null }, emom.map { it.side })
    }

    @Test
    fun `a planned double set of a one-sided lift is not done by a single one`() {
        val planned = Block(BuiltInExercise.Clean, rounds = 1, reps = 5, double = true).plannedSets()
        assertEquals(0, nextPlanned(planned, listOf(logged(BuiltInExercise.Clean, Side.Left))))
        assertNull(nextPlanned(planned, listOf(SetLog(0, BuiltInExercise.Clean, 16.0, 5, null, 0, double = true))))
    }

    @Test
    fun `the Armor Building Complex is planned with two bells`() {
        assertTrue(program(ProgramId.ArmorBuildingFormula).days[0].single().double)
    }

    @Test
    fun `ten thousand swings is 500 swings a session for 20 sessions`() {
        val swings = program(ProgramId.TenThousandSwings)
        assertEquals(20, swings.totalDays)
        swings.days.forEach { day -> assertEquals(500, day[0].totalReps) }
        assertEquals(BuiltInExercise.GobletSquat, swings.dayAfter(1)?.blocks?.get(1)?.exercise)
    }

    @Test
    fun `the armor building formula presses on the second day of three`() {
        val abf = program(ProgramId.ArmorBuildingFormula)
        assertEquals(BuiltInExercise.ArmorBuildingComplex, abf.dayAfter(0)?.blocks?.single()?.exercise)
        assertEquals(BuiltInExercise.Press, abf.dayAfter(1)?.blocks?.first()?.exercise)
        assertEquals(20, abf.dayAfter(2)?.plannedSets()?.size)
        assertEquals(18, abf.totalDays)
    }

    @Test
    fun `easy strength is 40 days and the countdown 55 squats`() {
        assertEquals(40, program(ProgramId.EasyStrength).totalDays)
        val countdown = assertNotNullDay(program(ProgramId.SwingSquatCountdown).dayAfter(0))
        assertEquals(55, countdown.blocks[0].totalReps)
        assertEquals(150, countdown.blocks[1].totalReps)
    }

    @Test
    fun `a program key is stored and read back`() {
        val keys = listOf(ProgramKey.BuiltIn(ProgramId.EasyStrength), ProgramKey.Custom("0123abcd"))
        keys.forEach { assertEquals(it, ProgramKey.parse(it.stored)) }
        assertEquals("EasyStrength", keys[0].stored)
        assertNull(ProgramKey.parse("NotAProgram"))
        assertNull(ProgramKey.parse("custom:"))
    }

    @Test
    fun `every built-in has days and each day a block`() {
        assertEquals(ProgramId.entries.size, builtInPrograms.size)
        assertEquals(ProgramId.entries.map { ProgramKey.BuiltIn(it) }, builtInPrograms.map { it.key })
    }

    @Test
    fun `simple and sinister is 100 one-arm swings and 10 get-ups a day`() {
        val day = assertNotNullDay(program(ProgramId.SimpleAndSinister).dayAfter(0))
        assertEquals(100, day.blocks[0].totalReps)
        assertEquals(10, day.blocks[1].totalReps)
        assertEquals(60, day.program.totalDays)
    }

    @Test
    fun `rite of passage presses most on the heavy day and least on the light`() {
        val (heavy, light, medium) = program(ProgramId.RiteOfPassage).days.map { it[0] }
        listOf(heavy, light, medium).forEach { assertEquals(5, it.rounds) }
        assertTrue(light.totalReps < medium.totalReps && medium.totalReps < heavy.totalReps)
    }

    @Test
    fun `snatch test prep has a day at the test pace of 100 in 5 minutes`() {
        val test = program(ProgramId.SnatchTestPrep).days[2].single()
        assertEquals(BlockKind.Emom, test.kind)
        assertEquals(5, test.rounds)
        assertEquals(100, test.totalReps)
    }

    @Test
    fun `round conditioning is four six-minute rounds a day`() {
        program(ProgramId.RoundConditioning).days.forEach { day ->
            assertEquals(4, day.size)
            day.forEach { assertEquals(6, it.plannedSets().size) }
        }
    }

    @Test
    fun `only the mixed and barbell programs leave the bells and the grapplers have their own family`() {
        builtInPrograms.filter {
            (it.key as ProgramKey.BuiltIn).id.family !in setOf(ProgramFamily.Mixed, ProgramFamily.Barbell)
        }
            .flatMap { it.days.flatten() }
            .forEach { assertTrue(it.exercise in kettlebellLifts) }
        assertEquals(
            listOf(
                ProgramId.GrapplerStrength,
                ProgramId.RoundConditioning,
                ProgramId.GripAndPull,
                ProgramId.ShoulderArmor,
            ),
            ProgramId.entries.filter { it.family == ProgramFamily.Grappling },
        )
    }

    @Test
    fun `the barbell family is Olympic foundations and a 5x5 of barbell lifts alone`() {
        assertEquals(
            listOf(ProgramId.OlympicFoundations, ProgramId.BarbellFiveByFive),
            ProgramId.entries.filter { it.family == ProgramFamily.Barbell },
        )
        listOf(ProgramId.OlympicFoundations, ProgramId.BarbellFiveByFive).forEach { id ->
            program(id).days.flatten().forEach {
                assertTrue(it.exercise in barbellLifts, it.exercise.key)
                assertNull(it.kg)
            }
        }
        assertEquals(36, program(ProgramId.BarbellFiveByFive).totalDays)
        assertEquals(24, program(ProgramId.OlympicFoundations).totalDays)
    }

    @Test
    fun `getting started has four programs and the swings build to 100 a day`() {
        assertEquals(
            listOf(
                ProgramId.KettlebellFoundations,
                ProgramId.FirstHundredSwings,
                ProgramId.BeginnerFullBody,
                ProgramId.GetUpPractice,
            ),
            ProgramId.entries.filter { it.family == ProgramFamily.Beginner },
        )
        val swings = program(ProgramId.FirstHundredSwings)
        assertEquals(listOf(50, 80, 100), swings.days.map { it[1].totalReps })
        assertEquals(BuiltInExercise.Deadlift, swings.dayAfter(0)?.blocks?.first()?.exercise)
    }

    @Test
    fun `a hold is only ever planned as sets of seconds`() {
        val holds = builtInPrograms.flatMap { it.days.flatten() }.filter { it.exercise.measure == Measure.Time }
        assertTrue(holds.isNotEmpty())
        holds.forEach { assertEquals(BlockKind.Sets, it.kind) }
        val plank = program(ProgramId.BodyweightBasics).days[0].last().plannedSets().first()
        assertTrue(plank.held)
        assertEquals(30, plank.reps)
    }

    @Test
    fun `the band suggested is the last one used for the exercise`() {
        val history = listOf(
            SetLog(0, BuiltInExercise.BandFacePull, 0.0, 15, null, 0, band = Band.of(BandLevel.Heavy)),
            SetLog(0, BuiltInExercise.BandPullApart, 0.0, 15, null, 1, band = Band.of(BandLevel.Light)),
        )
        val offered = bands(emptyList())
        val medium = Band.of(BandLevel.Medium)
        assertEquals(Band.of(BandLevel.Heavy), suggestedBand(BuiltInExercise.BandFacePull, history, offered, medium))
        assertEquals(medium, suggestedBand(BuiltInExercise.BandSquat, history, offered, medium))
        // One no longer offered, archived since, is not suggested.
        val heavyGone = offered.filterNot { it.level == BandLevel.Heavy }
        assertEquals(medium, suggestedBand(BuiltInExercise.BandFacePull, history, heavyGone, medium))
    }

    private fun assertNotNullDay(day: ProgramDay?): ProgramDay = kotlin.test.assertNotNull(day)
}
