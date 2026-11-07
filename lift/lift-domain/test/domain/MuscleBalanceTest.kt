package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MuscleBalanceTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, side: Side? = null) = SetLog.of(nextId++, exercise, 16.0, 10, side, nextId)

    private fun session(vararg sets: SetLog) = LiftSession("s$nextId", 0, 60_000, "", sets.toList())

    private fun MuscleBalance.load(group: MuscleGroup) = loads.single { it.group == group }

    @Test
    fun `a set counts as worked in the groups its exercise works most`() {
        // A goblet squat works the quads and glutes most: legs and hips.
        val balance = MuscleBalance.of(
            listOf(session(set(BuiltInExercise.GobletSquat), set(BuiltInExercise.GobletSquat)), session()),
        )!!
        assertEquals(MuscleGroup.entries, balance.loads.map { it.group })
        assertEquals(2.0, balance.load(MuscleGroup.Legs).worked)
        assertEquals(2.0, balance.load(MuscleGroup.Hips).worked)
        assertEquals(0.0, balance.load(MuscleGroup.Chest).total)
        assertEquals(2.0, balance.max)
    }

    @Test
    fun `a set counts as assisted where it works a muscle only also and once in each group`() {
        // A row works the lats and upper back most, and the biceps, rear delts and forearms also. The rear delts are
        // the back's, already worked; the biceps and forearms are both the arms', counted once.
        val balance = MuscleBalance.of(listOf(session(set(BuiltInExercise.BarbellRow)), session()))!!
        assertEquals(MuscleLoad(MuscleGroup.Back, worked = 1.0, assisted = 0.0), balance.load(MuscleGroup.Back))
        assertEquals(MuscleLoad(MuscleGroup.Arms, worked = 0.0, assisted = 1.0), balance.load(MuscleGroup.Arms))
        assertEquals(
            listOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Core, MuscleGroup.Hips, MuscleGroup.Legs),
            balance.untrained,
        )
    }

    @Test
    fun `a set of one side counts half`() {
        val balance = MuscleBalance.of(
            listOf(session(set(BuiltInExercise.Press, Side.Left), set(BuiltInExercise.Press, Side.Right)), session()),
        )!!
        assertEquals(1.0, balance.load(MuscleGroup.Shoulders).worked)
    }

    @Test
    fun `a custom exercise counts as assisted in each group of its focus`() {
        val curl = CustomExercise("c", "Hammer curl", Equipment.Kettlebell, Measure.Reps, false, Focus.UpperBody)
        val carry = CustomExercise("d", "Farmer carry", Equipment.Kettlebell, Measure.Time, false, Focus.FullBody)
        val gone = UnknownExercise("custom:gone", Equipment.Kettlebell, Measure.Reps)
        val balance = MuscleBalance.of(listOf(session(set(curl), set(gone)), session(set(carry))))!!
        listOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Back, MuscleGroup.Arms).forEach {
            assertEquals(MuscleLoad(it, worked = 0.0, assisted = 2.0), balance.load(it))
        }
        listOf(MuscleGroup.Core, MuscleGroup.Hips, MuscleGroup.Legs).forEach {
            assertEquals(MuscleLoad(it, worked = 0.0, assisted = 1.0), balance.load(it))
        }
        assertEquals(emptyList(), balance.untrained)
    }

    @Test
    fun `fewer than two sessions give no balance and two empty ones name every group`() {
        assertNull(MuscleBalance.of(emptyList()))
        assertNull(MuscleBalance.of(listOf(session(set(BuiltInExercise.Snatch)))))
        assertEquals(MuscleGroup.entries, MuscleBalance.of(listOf(session(), session()))!!.untrained)
    }

    @Test
    fun `every muscle is in exactly one group`() {
        Muscle.entries.forEach { muscle ->
            assertEquals(1, MuscleGroup.entries.count { muscle in it.muscles }, "$muscle")
        }
    }

    @Test
    fun `a density block plans a round a minute of its cap for the week's balance`() {
        fun week(block: Block) = Program(ProgramKey.Custom("p"), listOf(listOf(block)), daysPerWeek = 2, weeks = 4)
        val density = Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Density)
        val sets = Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10)
        assertEquals(week(sets).weekBalance(), week(density).weekBalance())
    }
}
