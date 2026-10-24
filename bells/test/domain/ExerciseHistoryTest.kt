package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseHistoryTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int, double: Boolean = false) =
        SetLog(nextId++, exercise, kg, reps, null, nextId, double)

    private fun session(uid: String, startedAt: Long, vararg sets: SetLog) =
        LiftSession(uid, startedAt, startedAt + 60_000, "", sets.toList())

    @Test
    fun `it counts the sessions an exercise was done in and the last of them`() {
        // Newest first, as the repository holds them.
        val sessions = listOf(
            session("c", 3_000, set(BuiltInExercise.Press, 16.0, 5), set(BuiltInExercise.Press, 16.0, 5)),
            session("b", 2_000, set(BuiltInExercise.GobletSquat, 24.0, 10)),
            session("a", 1_000, set(BuiltInExercise.Press, 12.0, 8)),
        )
        val history = exerciseHistory(BuiltInExercise.Press, sessions)
        assertEquals(2, history.timesDone)
        assertEquals(3_000L, history.lastDoneAtEpochMillis)
        assertEquals(listOf("c", "a"), history.recent.map { it.uid })
        assertEquals(listOf(2, 1), history.recent.map { it.sets.size })
    }

    @Test
    fun `its records keep one bell and two apart`() {
        val sessions = listOf(
            session("b", 2_000, set(BuiltInExercise.Clean, 16.0, 5, double = true)),
            session("a", 1_000, set(BuiltInExercise.Clean, 24.0, 5)),
        )
        val records = exerciseHistory(BuiltInExercise.Clean, sessions).records
        assertEquals(listOf(false, true), records.map { it.double })
    }

    @Test
    fun `only the latest sessions are listed`() {
        val sessions = (12 downTo 1).map { session("s$it", it * 1_000L, set(BuiltInExercise.Snatch, 16.0, 10)) }
        val history = exerciseHistory(BuiltInExercise.Snatch, sessions, limit = 3)
        assertEquals(12, history.timesDone)
        assertEquals(listOf("s12", "s11", "s10"), history.recent.map { it.uid })
    }

    @Test
    fun `an exercise never done has no history`() {
        val history =
            exerciseHistory(BuiltInExercise.Halo, listOf(session("a", 1_000, set(BuiltInExercise.Row, 16.0, 8))))
        assertEquals(0, history.timesDone)
        assertNull(history.lastDoneAtEpochMillis)
        assertEquals(emptyList(), history.recent)
        assertEquals(emptyList(), history.records)
    }
}
