package dev.justinsmith.interval.trends

import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.bellWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY_MILLIS = 86_400_000L
private const val MAX_BPM = 190

class TrendsTest {
    private fun dayOf(millis: Long): Long = millis.floorDiv(DAY_MILLIS)

    private fun trendsOf(
        history: List<HistoryEntry> = emptyList(),
        sessions: List<LiftSession> = emptyList(),
        classes: List<SportClass> = emptyList(),
        unit: WeightUnit = WeightUnit.Kg,
    ) = trends(history, sessions, classes, TODAY, ::dayOf, unit, MAX_BPM, restingBpm = null)

    private fun workout(day: Long, minutes: Int = 20, samples: List<Int>? = null) = HistoryEntry(
        finishedAtEpochMillis = noonOf(day),
        config = IntervalConfig(),
        elapsedSeconds = minutes * 60,
        endedEarly = false,
        heartRateSamples = samples,
    )

    /** A workout run straight through with a rest of a minute between works, its heart falling [slope] a sample. */
    private fun recovered(day: Long, slope: Int): HistoryEntry {
        val config = IntervalConfig(workSeconds = 60, restSeconds = 60, rounds = 3)
        val finished = noonOf(day)
        val seconds = config.totalSeconds
        return HistoryEntry(
            finishedAtEpochMillis = finished,
            config = config,
            elapsedSeconds = seconds,
            endedEarly = false,
            // Timed, not logged by hand: a timed run keeps its work.
            workActiveSeconds = 3 * 60,
            startedAtEpochMillis = finished - seconds * 1_000L,
            heartRateSamples = List(seconds / 5 + 1) { 200 - it * slope },
        )
    }

    private fun set(kg: Double, reps: Int, at: Long, warmUp: Boolean = false, id: Long = at) =
        SetLog(id, BuiltInExercise.Snatch, Load.Bell(kg), reps, side = null, loggedAtEpochMillis = at, warmUp = warmUp)

    private fun session(
        uid: String,
        day: Long,
        vararg sets: SetLog,
        program: ProgramKey? = null,
        samples: List<Int>? = null,
    ) = LiftSession(
        uid = uid,
        startedAtEpochMillis = noonOf(day) - 30 * 60_000L,
        finishedAtEpochMillis = noonOf(day),
        title = uid,
        sets = sets.toList(),
        program = program,
        heartRateSamples = samples,
    )

    @Test
    fun `twelve weeks end with this one and count both sides' time`() {
        val trends = trendsOf(
            history = listOf(workout(TODAY, minutes = 20), workout(TODAY - 7, minutes = 15)),
            sessions = listOf(session("s", TODAY, set(16.0, 10, noonOf(TODAY)))),
        )
        assertEquals(TREND_WEEKS, trends.weeks.size)
        val now = trends.weeks.last()
        assertTrue(now.current)
        assertEquals(weekStartOf(TODAY), now.weekStart)
        assertEquals(20 + 30, now.minutes)
        assertEquals(15, trends.weeks[TREND_WEEKS - 2].minutes)
        assertEquals(weekStartOf(TODAY) - 77, trends.weeks.first().weekStart)
    }

    @Test
    fun `the weight lifted leaves warm-ups out in either unit`() {
        val at = noonOf(TODAY)
        val lifted = session("s", TODAY, set(8.0, 10, at, warmUp = true), set(16.0, 10, at + 1))
        assertEquals(160.0, trendsOf(sessions = listOf(lifted)).weeks.last().lifted)
        val inPounds = trendsOf(sessions = listOf(lifted), unit = WeightUnit.Lb).weeks.last().lifted
        assertEquals(bellWeight(16.0, WeightUnit.Lb) * 10, inPounds)
    }

    @Test
    fun `the heart's load counts workouts sessions and classes`() {
        val hard = List(120) { 175 }
        val recorded = SportClass(1, Sport.Bjj, TODAY, noonOf(TODAY), hard.map { ClassSample(it, 0f) }, emptyList())
        val trends = trendsOf(
            history = listOf(workout(TODAY, samples = hard)),
            sessions = listOf(session("s", TODAY, set(16.0, 10, noonOf(TODAY)), samples = hard)),
            classes = listOf(recorded),
        )
        assertEquals(3 * heartLoad(hard, MAX_BPM), trends.weeks.last().heartLoad)
        assertTrue(trends.hasHeartLoad)
    }

    @Test
    fun `recovery takes a minute's drop from workouts run straight through`() {
        val history = listOf(recovered(TODAY - 20, slope = 1), workout(TODAY - 10, samples = List(100) { 150 }))
        val points = trendsOf(history = history).recovery
        assertEquals(listOf(RecoveryPoint(TODAY - 20, 12)), points)
    }

    @Test
    fun `recovery says which way it is going once both ends have three workouts`() {
        val slower = (0..5).map { recovered(TODAY - 30 + it, slope = 2) } + recovered(TODAY, slope = 1)
        assertNull(trendsOf(history = slower.take(5)).recoveryDirection)
        val faster = (0..2).map { recovered(TODAY - 30 + it, slope = 1) } +
            (0..2).map { recovered(TODAY - 10 + it, slope = 2) }
        assertEquals(RecoveryDirection.Faster, trendsOf(history = faster).recoveryDirection)
        val steady = (0..5).map { recovered(TODAY - 30 + it, slope = 1) }
        assertEquals(RecoveryDirection.Steady, trendsOf(history = steady).recoveryDirection)
    }

    @Test
    fun `records are listed newest first and counted over the weeks`() {
        val old = TODAY - 200
        val sessions = listOf(
            session("third", TODAY, set(16.0, 8, noonOf(TODAY))),
            session("second", TODAY - 3, set(20.0, 5, noonOf(TODAY - 3))),
            session("first", old, set(16.0, 5, noonOf(old))),
        )
        val trends = trendsOf(sessions = sessions)
        assertEquals(listOf("third", "second", "first"), trends.records.map { it.sessionUid })
        assertEquals(2, trends.recordsInWeeks)
        val heaviest = trends.records.first().record as PersonalRecord.Weighted
        assertEquals(20.0, heaviest.heaviestKg)
        assertEquals(8, heaviest.mostReps)
    }

    @Test
    fun `a test's line keeps to the latest attempt's bell`() {
        val test = ProgramKey.Test(TestId.SnatchTest)
        fun attempt(uid: String, day: Long, kg: Double, reps: Int) =
            session(uid, day, set(kg, reps, noonOf(day)), program = test)
        val sessions = listOf(
            attempt("c", TODAY, 24.0, 90),
            attempt("b", TODAY - 40, 16.0, 100),
            attempt("a", TODAY - 80, 24.0, 70),
        )
        val trend = trendsOf(sessions = sessions).tests.single()
        assertEquals(TestId.SnatchTest, trend.test)
        assertEquals(Load.Bell(24.0), trend.load)
        assertEquals(listOf(70, 90), trend.results)
    }

    @Test
    fun `nothing logged leaves every card out`() {
        val trends = trendsOf()
        assertTrue(trends.empty)
        assertNull(trends.weeks.usual { it.minutes.toDouble() })
    }

    @Test
    fun `this week is set against the usual of the weeks before it from the first trained`() {
        val history = listOf(workout(TODAY - 14, minutes = 30), workout(TODAY - 7, minutes = 60), workout(TODAY))
        assertEquals(45.0, trendsOf(history = history).weeks.usual { it.minutes.toDouble() })
    }
}
