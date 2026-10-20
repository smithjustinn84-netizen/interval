package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int) = SetLog(nextId++, exercise, kg, reps, null, nextId)

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
    fun `weights are written without needless decimals`() {
        assertEquals("24", formatKg(24.0))
        assertEquals("1.5", formatKg(1.5))
        assertEquals("2400", formatKg(2400.0))
    }
}
