package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int, double: Boolean = false) =
        SetLog(nextId++, exercise, kg, reps, null, nextId, double)

    private fun session(vararg sets: SetLog) = LiftSession("s", 0, 60_000, "", sets.toList())

    @Test
    fun `a record is the heaviest bell with its best reps and the most reps with any bell`() {
        val records = personalRecords(
            listOf(
                set(BuiltInExercise.Press, 16.0, 8),
                set(BuiltInExercise.Press, 20.0, 3),
                set(BuiltInExercise.Press, 20.0, 5),
                set(BuiltInExercise.Press, 12.0, 12),
            ),
        )
        assertEquals(listOf(PersonalRecord(BuiltInExercise.Press, 20.0, 5, 12, 12.0)), records)
    }

    @Test
    fun `records follow the catalogue's order`() {
        val records =
            personalRecords(listOf(set(BuiltInExercise.GetUp, 16.0, 1), set(BuiltInExercise.TwoHandSwing, 24.0, 10)))
        assertEquals(listOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.GetUp), records.map { it.exercise })
    }

    @Test
    fun `a session sets a record with a heavier bell or more reps and not by matching one`() {
        val before = listOf(set(BuiltInExercise.TwoHandSwing, 24.0, 10), set(BuiltInExercise.Press, 20.0, 5))
        val heavier = session(set(BuiltInExercise.TwoHandSwing, 28.0, 5), set(BuiltInExercise.Press, 20.0, 5))
        assertEquals(listOf(BuiltInExercise.TwoHandSwing), newRecords(before, heavier).map { it.exercise })
        val first = session(set(BuiltInExercise.Snatch, 16.0, 10))
        assertEquals(listOf(BuiltInExercise.Snatch), newRecords(before, first).map { it.exercise })
    }

    @Test
    fun `a session's weight moved and reps add up its sets`() {
        val done = session(set(BuiltInExercise.TwoHandSwing, 24.0, 10), set(BuiltInExercise.GetUp, 20.0, 5))
        assertEquals(340.0, done.tonnageKg)
        assertEquals(15, done.reps)
        assertEquals(60, done.elapsedSeconds(nowMillis = 0))
    }

    @Test
    fun `a set with two bells moves twice the weight`() {
        assertEquals(320.0, session(set(BuiltInExercise.Press, 16.0, 10, double = true)).tonnageKg)
    }

    @Test
    fun `records with two bells are kept apart from those with one`() {
        val records =
            personalRecords(
                listOf(set(BuiltInExercise.Press, 24.0, 5), set(BuiltInExercise.Press, 20.0, 3, double = true)),
            )
        assertEquals(
            listOf(
                PersonalRecord(BuiltInExercise.Press, 24.0, 5, 5, 24.0),
                PersonalRecord(BuiltInExercise.Press, 20.0, 3, 3, 20.0, true),
            ),
            records,
        )
        // A first double press is a record of its own, though the single press is heavier.
        val doubles = session(set(BuiltInExercise.Press, 16.0, 5, double = true))
        assertEquals(listOf(true), newRecords(listOf(set(BuiltInExercise.Press, 24.0, 5)), doubles).map { it.double })
    }

    @Test
    fun `a lift is doubled only if it can be`() {
        assertEquals(true, BuiltInExercise.Clean.doubled(requested = true))
        assertEquals(false, BuiltInExercise.GetUp.doubled(requested = true))
        assertEquals(true, BuiltInExercise.ArmorBuildingComplex.doubled(requested = false))
        assertEquals(false, BuiltInExercise.Press.sided(double = true))
        assertEquals(true, BuiltInExercise.Press.sided(double = false))
    }

    @Test
    fun `the built-ins keep the keys their sets were stored under`() {
        assertEquals(
            listOf(
                "TwoHandSwing", "OneHandSwing", "Clean", "Press", "Snatch", "GetUp", "GobletSquat", "Row", "Jerk",
                "LongCycle", "Halo", "FrontSquat", "ArmorBuildingComplex",
            ),
            kettlebellLifts.map { it.key },
        )
        BuiltInExercise.entries.forEach { assertEquals(it, Exercise.parse(it.key)) }
        assertNull(Exercise.parse("Deadlift"))
    }

    @Test
    fun `an exercise the user added is found by its key among theirs`() {
        val plank = CustomExercise("a1", "Copenhagen plank", Equipment.Bodyweight, Measure.Time, true, Focus.Core)
        assertEquals("custom:a1", plank.key)
        assertEquals(plank, Exercise.parse("custom:a1", listOf(plank)))
        assertNull(Exercise.parse("custom:b2", listOf(plank)))
        assertEquals(Doubles.Never, plank.doubles)
    }

    @Test
    fun `the catalogue runs built-ins then the user's by name then unknowns`() {
        val zercher = CustomExercise("z", "zercher carry", Equipment.Kettlebell, Measure.Reps, false, Focus.FullBody)
        val bear = CustomExercise("b", "Bear crawl", Equipment.Bodyweight, Measure.Time, false, Focus.FullBody)
        val unknown = UnknownExercise("Deadlift", Equipment.Kettlebell, Measure.Reps)
        assertEquals(
            listOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.Seal, bear, zercher, unknown),
            listOf(unknown, zercher, BuiltInExercise.Seal, bear, BuiltInExercise.TwoHandSwing)
                .sortedWith(Exercise.order),
        )
        assertEquals(Doubles.Optional, zercher.doubles)
    }

    @Test
    fun `the starter catalogue has 68 exercises across four kinds of equipment`() {
        assertEquals(
            mapOf(Equipment.Kettlebell to 13, Equipment.Bodyweight to 22, Equipment.Band to 15, Equipment.Mat to 18),
            BuiltInExercise.entries.groupingBy { it.equipment }.eachCount(),
        )
        assertEquals(BuiltInExercise.entries.take(13), kettlebellLifts)
    }

    @Test
    fun `only kettlebell lifts can be done with two bells`() {
        BuiltInExercise.entries.filter { it.equipment != Equipment.Kettlebell }.forEach {
            assertEquals(Doubles.Never, it.doubles, it.key)
        }
    }

    @Test
    fun `holds are the exercises measured in time`() {
        assertEquals(
            listOf(
                BuiltInExercise.WallSit,
                BuiltInExercise.Plank,
                BuiltInExercise.SidePlank,
                BuiltInExercise.HollowHold,
                BuiltInExercise.Swimming,
            ),
            BuiltInExercise.entries.filter { it.measure == Measure.Time },
        )
    }

    @Test
    fun `weights are written without needless decimals`() {
        assertEquals("24", formatKg(24.0))
        assertEquals("1.5", formatKg(1.5))
        assertEquals("2400", formatKg(2400.0))
    }
}
