package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramCoverageTest {
    private val id = { program: Program -> (program.key as ProgramKey.BuiltIn).id }

    @Test
    fun `a week of a program counts each planned set as a set done counts`() {
        // Two days a week of the one day: 3 goblet squats and 3 rows a side, each one-sided set counting half.
        val balance = program(ProgramId.GobletSquatRow).weekBalance()
        val legs = balance.loads.single { it.group == MuscleGroup.Legs }
        val back = balance.loads.single { it.group == MuscleGroup.Back }
        assertEquals(6.0, legs.worked)
        assertEquals(6.0, back.worked)
        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Shoulders), balance.untrained)
    }

    @Test
    fun `a week takes the days in turn`() {
        // Program Minimum's days are swings, get-ups, swings, get-ups: 5 get-ups a side twice, a side counting half.
        val balance = program(ProgramId.ProgramMinimum).weekBalance()
        assertEquals(10.0, balance.loads.single { it.group == MuscleGroup.Shoulders }.worked)
    }

    /**
     * What each built-in leaves untrained in a week. Most are bells alone, with nothing for the chest; a gap that
     * isn't here fails, so a change to a program says what it leaves out.
     */
    @Test
    fun `each built-in leaves untrained only the groups it is known to`() {
        val chest = setOf(MuscleGroup.Chest)
        val chestLegs = setOf(MuscleGroup.Chest, MuscleGroup.Legs)
        val chestShoulders = setOf(MuscleGroup.Chest, MuscleGroup.Shoulders)
        val known = mapOf(
            ProgramId.DailySwingsGetUps to chestLegs,
            ProgramId.PressLadders to chestLegs,
            ProgramId.CleanPressEmom to chestLegs,
            ProgramId.GobletSquatRow to chestShoulders,
            ProgramId.EasyStrength to chest,
            ProgramId.ArmorBuildingFormula to chest,
            ProgramId.TenThousandSwings to emptySet(),
            ProgramId.SwingSquatCountdown to chestShoulders,
            ProgramId.OneStopShop to chest,
            ProgramId.StrongForLife to emptySet(),
            ProgramId.SimpleAndSinister to chestLegs,
            ProgramId.ProgramMinimum to chestLegs,
            ProgramId.RiteOfPassage to chestLegs,
            ProgramId.IronCardio to chest,
            ProgramId.SnatchTestPrep to chestLegs,
            ProgramId.LongCycleEmom to chest,
            ProgramId.FifteenMinuteKettlebell to chest,
            ProgramId.GrapplerStrength to chest,
            ProgramId.RoundConditioning to chest,
            ProgramId.GripAndPull to setOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Legs),
            ProgramId.ShoulderArmor to chestLegs,
            ProgramId.BjjKettlebell to chest,
            ProgramId.BjjOneBell to chest,
            ProgramId.KettlebellFoundations to emptySet(),
            ProgramId.FirstHundredSwings to setOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Legs),
            ProgramId.BeginnerFullBody to emptySet(),
            ProgramId.GetUpPractice to chest,
            ProgramId.BodyweightBasics to emptySet(),
            ProgramId.BodyweightEmom to emptySet(),
            ProgramId.BellsAndBodyweight to emptySet(),
            ProgramId.BandsAndBells to emptySet(),
            ProgramId.CoreOnTheMat to chestLegs,
            ProgramId.OlympicFoundations to chest,
            ProgramId.BarbellFiveByFive to emptySet(),
        )
        assertEquals(known, builtInPrograms.associate { id(it) to it.weekBalance().untrained.toSet() })
    }

    /**
     * Each day of each built-in as it plans: its sets and their reps in all. A follower's days done are counted
     * against the program as it now is, so a built-in changed under them must plan the same sets; a range added
     * starts at the reps it had.
     */
    @Test
    fun `each built-in day plans the sets it always has`() {
        val planned = mapOf(
            ProgramId.DailySwingsGetUps to listOf(20 to 110),
            ProgramId.PressLadders to listOf(23 to 86, 34 to 98, 35 to 135),
            ProgramId.CleanPressEmom to listOf(20 to 60),
            ProgramId.GobletSquatRow to listOf(9 to 90),
            ProgramId.EasyStrength to listOf(17 to 157),
            ProgramId.ArmorBuildingFormula to listOf(20 to 20, 20 to 100),
            ProgramId.TenThousandSwings to listOf(50 to 560, 35 to 530, 35 to 530, 35 to 550),
            ProgramId.SwingSquatCountdown to listOf(20 to 205),
            ProgramId.OneStopShop to listOf(21 to 329),
            ProgramId.StrongForLife to listOf(15 to 201, 18 to 215),
            ProgramId.SimpleAndSinister to listOf(20 to 110),
            ProgramId.ProgramMinimum to listOf(12 to 120, 10 to 10),
            ProgramId.RiteOfPassage to listOf(60 to 200, 40 to 160, 50 to 200),
            ProgramId.IronCardio to listOf(25 to 160, 30 to 60),
            ProgramId.SnatchTestPrep to listOf(10 to 80, 12 to 120, 5 to 100),
            ProgramId.LongCycleEmom to listOf(10 to 60, 20 to 180, 12 to 60),
            ProgramId.FifteenMinuteKettlebell to listOf(9 to 72, 15 to 155),
            ProgramId.GrapplerStrength to listOf(19 to 95, 29 to 91),
            ProgramId.RoundConditioning to listOf(24 to 234, 24 to 174),
            ProgramId.GripAndPull to listOf(40 to 360),
            ProgramId.ShoulderArmor to listOf(25 to 100),
            ProgramId.BjjKettlebell to listOf(15 to 15, 16 to 180),
            ProgramId.BjjOneBell to listOf(19 to 183, 18 to 206),
            ProgramId.KettlebellFoundations to listOf(8 to 66, 13 to 72),
            ProgramId.FirstHundredSwings to listOf(12 to 70, 12 to 100, 12 to 120),
            ProgramId.BeginnerFullBody to listOf(18 to 126, 14 to 104),
            ProgramId.GetUpPractice to listOf(14 to 36),
            ProgramId.BodyweightBasics to listOf(12 to 189, 16 to 194),
            ProgramId.BodyweightEmom to listOf(20 to 210, 30 to 330),
            ProgramId.BellsAndBodyweight to listOf(18 to 180, 32 to 151),
            ProgramId.BandsAndBells to listOf(21 to 213, 15 to 165),
            ProgramId.CoreOnTheMat to listOf(17 to 218),
            ProgramId.OlympicFoundations to listOf(14 to 54, 14 to 54, 15 to 30),
            ProgramId.BarbellFiveByFive to listOf(15 to 75, 11 to 55),
        )
        assertEquals(
            planned,
            builtInPrograms.associate { p ->
                id(p) to
                    p.days.indices.map { d -> p.day(d + 1).plannedSets().let { s -> s.size to s.sumOf { it.reps } } }
            },
        )
        builtInPrograms.flatMap { it.days.flatten() }.forEach { block ->
            block.range?.let { assertEquals(it.bottom, block.reps, "${block.exercise} starts at its range's bottom") }
        }
    }
}
