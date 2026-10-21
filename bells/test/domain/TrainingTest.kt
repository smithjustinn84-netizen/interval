package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int, double: Boolean = false) =
        SetLog(nextId++, exercise, kg, reps, null, nextId, double)

    private fun session(vararg sets: SetLog) = LiftSession("s", 0, 60_000, "", sets.toList())

    @Test
    fun `a record is the heaviest bell with its best reps and the most reps with any bell`() {
        val records = personalRecords(
            listOf(
                set(Exercise.Press, 16.0, 8),
                set(Exercise.Press, 20.0, 3),
                set(Exercise.Press, 20.0, 5),
                set(Exercise.Press, 12.0, 12),
            ),
        )
        assertEquals(listOf(PersonalRecord(Exercise.Press, 20.0, 5, 12, 12.0)), records)
    }

    @Test
    fun `records follow the catalogue's order`() {
        val records = personalRecords(listOf(set(Exercise.GetUp, 16.0, 1), set(Exercise.TwoHandSwing, 24.0, 10)))
        assertEquals(listOf(Exercise.TwoHandSwing, Exercise.GetUp), records.map { it.exercise })
    }

    @Test
    fun `a session sets a record with a heavier bell or more reps and not by matching one`() {
        val before = listOf(set(Exercise.TwoHandSwing, 24.0, 10), set(Exercise.Press, 20.0, 5))
        val heavier = session(set(Exercise.TwoHandSwing, 28.0, 5), set(Exercise.Press, 20.0, 5))
        assertEquals(listOf(Exercise.TwoHandSwing), newRecords(before, heavier).map { it.exercise })
        val first = session(set(Exercise.Snatch, 16.0, 10))
        assertEquals(listOf(Exercise.Snatch), newRecords(before, first).map { it.exercise })
    }

    @Test
    fun `a session's weight moved and reps add up its sets`() {
        val done = session(set(Exercise.TwoHandSwing, 24.0, 10), set(Exercise.GetUp, 20.0, 5))
        assertEquals(340.0, done.tonnageKg)
        assertEquals(15, done.reps)
        assertEquals(60, done.elapsedSeconds(nowMillis = 0))
    }

    @Test
    fun `a set with two bells moves twice the weight`() {
        assertEquals(320.0, session(set(Exercise.Press, 16.0, 10, double = true)).tonnageKg)
    }

    @Test
    fun `records with two bells are kept apart from those with one`() {
        val records = personalRecords(listOf(set(Exercise.Press, 24.0, 5), set(Exercise.Press, 20.0, 3, double = true)))
        assertEquals(
            listOf(
                PersonalRecord(Exercise.Press, 24.0, 5, 5, 24.0),
                PersonalRecord(Exercise.Press, 20.0, 3, 3, 20.0, true),
            ),
            records,
        )
        // A first double press is a record of its own, though the single press is heavier.
        val doubles = session(set(Exercise.Press, 16.0, 5, double = true))
        assertEquals(listOf(true), newRecords(listOf(set(Exercise.Press, 24.0, 5)), doubles).map { it.double })
    }

    @Test
    fun `a lift is doubled only if it can be`() {
        assertEquals(true, Exercise.Clean.doubled(requested = true))
        assertEquals(false, Exercise.GetUp.doubled(requested = true))
        assertEquals(true, Exercise.ArmorBuildingComplex.doubled(requested = false))
        assertEquals(false, Exercise.Press.sided(double = true))
        assertEquals(true, Exercise.Press.sided(double = false))
    }

    @Test
    fun `weights are written without needless decimals`() {
        assertEquals("24", formatKg(24.0))
        assertEquals("1.5", formatKg(1.5))
        assertEquals("2400", formatKg(2400.0))
    }
}
