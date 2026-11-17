package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseHistoryTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int, double: Boolean = false) =
        SetLog.of(nextId++, exercise, kg, reps, null, nextId, double)

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

    @Test
    fun `the same set in a row folds into one and order is kept`() {
        val left = SetLog.of(1, BuiltInExercise.Press, 16.0, 5, Side.Left, 0)
        val runs = collapsedSets(
            listOf(
                left,
                left.copy(id = 2),
                left.copy(id = 3, side = Side.Right),
                left.copy(id = 4),
                left.copy(id = 5, load = Load.Bell(20.0)),
            ),
        )
        assertEquals(listOf(2, 1, 1, 1), runs.map { it.count })
        assertEquals(listOf(Side.Left, Side.Right, Side.Left, Side.Left), runs.map { it.set.side })
        assertEquals(20.0, runs.last().set.load.kg)
    }
}

class ExerciseTrendTest {
    private var nextId = 1L

    private fun session(startedAt: Long, vararg sets: SetLog) = PastSession("s$startedAt", startedAt, sets.toList())

    private fun set(exercise: Exercise, kg: Double, reps: Int, seconds: Int? = null, double: Boolean = false) =
        SetLog.of(nextId++, exercise, kg, reps, null, nextId, double, seconds = seconds)

    @Test
    fun `a weighted lift trends by its best set's work each session oldest first`() {
        val recent = listOf(
            session(3_000, set(BuiltInExercise.Press, 24.0, 3), set(BuiltInExercise.Press, 16.0, 10)),
            session(2_000, set(BuiltInExercise.Press, 16.0, 8)),
            session(1_000, set(BuiltInExercise.Press, 16.0, 5)),
        )
        val trend = exerciseTrend(BuiltInExercise.Press, recent)
        assertEquals(TrendMeasure.Work, trend?.measure)
        assertEquals(listOf(80.0, 128.0, 160.0), trend?.points?.map { it.value })
        assertEquals(TrendPoint(3_000, 160.0, 16.0, 10), trend?.points?.last())
        assertEquals(80.0, trend?.min)
        assertEquals(TrendPoint(1_000, 80.0, 16.0, 5), trend?.first)
    }

    @Test
    fun `a trend marks where it went to another bell`() {
        val recent = listOf(
            session(4_000, set(BuiltInExercise.Press, 16.0, 6)),
            session(3_000, set(BuiltInExercise.Press, 20.0, 5)),
            session(2_000, set(BuiltInExercise.Press, 16.0, 12)),
            session(1_000, set(BuiltInExercise.Press, 16.0, 8)),
        )
        // Up to 20 kg in the third session, back to 16 kg in the fourth.
        assertEquals(listOf(2, 3), exerciseTrend(BuiltInExercise.Press, recent)?.bellChanges)
        val pushUps = listOf(
            session(2_000, set(BuiltInExercise.PushUp, 0.0, 15)),
            session(1_000, set(BuiltInExercise.PushUp, 0.0, 12)),
        )
        assertEquals(emptyList(), exerciseTrend(BuiltInExercise.PushUp, pushUps)?.bellChanges)
    }

    @Test
    fun `a tie of work goes to the heavier bell`() {
        val recent = listOf(
            session(2_000, set(BuiltInExercise.TwoHandSwing, 16.0, 15), set(BuiltInExercise.TwoHandSwing, 24.0, 10)),
            session(1_000, set(BuiltInExercise.TwoHandSwing, 16.0, 10)),
        )
        val latest = exerciseTrend(BuiltInExercise.TwoHandSwing, recent)?.points?.last()
        assertEquals(24.0, latest?.kg)
        assertEquals(10, latest?.reps)
    }

    @Test
    fun `a set with two bells counts the bell's weight`() {
        val recent = listOf(
            session(2_000, set(BuiltInExercise.Clean, 16.0, 5, double = true)),
            session(1_000, set(BuiltInExercise.Clean, 16.0, 4, double = true)),
        )
        assertEquals(listOf(64.0, 80.0), exerciseTrend(BuiltInExercise.Clean, recent)?.points?.map { it.value })
    }

    @Test
    fun `the trend keeps its latest sessions while the page lists fewer`() {
        val sessions = (30 downTo 1).map {
            LiftSession("s$it", it * 1_000L, it * 1_000L + 60_000, "", listOf(set(BuiltInExercise.Snatch, 16.0, it)))
        }
        val history = exerciseHistory(BuiltInExercise.Snatch, sessions)
        assertEquals(RECENT_SESSIONS, history.recent.size)
        val trend = exerciseTrend(BuiltInExercise.Snatch, history.trendSessions)
        assertEquals(TREND_SESSIONS, trend?.points?.size)
        assertEquals((7..30).map { it * 1_000L }, trend?.points?.map { it.startedAtEpochMillis })
    }

    @Test
    fun `bodyweight with nothing added trends by reps and a hold by its length`() {
        val pushUps = listOf(
            session(2_000, set(BuiltInExercise.PushUp, 0.0, 15)),
            session(1_000, set(BuiltInExercise.PushUp, 0.0, 12)),
        )
        assertEquals(TrendMeasure.Reps, exerciseTrend(BuiltInExercise.PushUp, pushUps)?.measure)
        assertEquals(listOf(12.0, 15.0), exerciseTrend(BuiltInExercise.PushUp, pushUps)?.points?.map { it.value })
        val holds = listOf(
            session(2_000, set(BuiltInExercise.Plank, 0.0, 0, seconds = 45)),
            session(
                1_000,
                set(BuiltInExercise.Plank, 0.0, 0, seconds = 30),
                set(BuiltInExercise.Plank, 0.0, 0, seconds = 40),
            ),
        )
        assertEquals(TrendMeasure.Seconds, exerciseTrend(BuiltInExercise.Plank, holds)?.measure)
        assertEquals(listOf(40.0, 45.0), exerciseTrend(BuiltInExercise.Plank, holds)?.points?.map { it.value })
    }

    @Test
    fun `one session is no trend`() {
        assertNull(exerciseTrend(BuiltInExercise.Press, listOf(session(1_000, set(BuiltInExercise.Press, 16.0, 5)))))
    }
}
