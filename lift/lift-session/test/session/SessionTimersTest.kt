@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.HoldClock
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.SafetyLine
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingKeepAlive
import dev.justinsmith.lift.fakes.RecordingVoice
import dev.justinsmith.lift.platform.Coaching
import dev.justinsmith.lift.platform.TimerRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The process's timers on their own: what the screen and the platform's keep-alive rely on, no ViewModel at all. */
class SessionTimersTest {
    private val storage = FakeLiftStorage()

    /** The wall clock, which the timers tell the platform by and never time by. */
    private var now = 50_000L
    private val repository = storage.repository { now }
    private val beeper = RecordingBeeper()
    private val keepAlive = RecordingKeepAlive()
    private val voice = RecordingVoice()

    /** Timers ticking on the test's scheduler, under its background scope, so none outlives the test. */
    private fun TestScope.timers() = SessionTimers(
        repository.sets,
        WallClock { now },
        testScheduler.timeSource,
        beeper,
        keepAlive,
        voice,
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job)),
    )

    /** Lets [millis] of the scheduler's time pass, and the tick after it. */
    private fun TestScope.run(millis: Long) {
        advanceTimeBy(millis + SessionTimers.TICK_MILLIS)
        runCurrent()
    }

    @Test
    fun `a rest beeps into its end and lets the keep-alive go`() = runTest {
        val timers = timers()
        timers.startRest(30)
        assertEquals(listOf<TimerRun>(TimerRun.Rest(now, now + 30_000)), keepAlive.runs)
        run(30_000)
        assertEquals(listOf(EmomCue.Countdown, EmomCue.Start), beeper.cues.takeLast(2))
        assertNull(timers.state.value.rest)
        assertEquals(1, keepAlive.stops)
    }

    @Test
    fun `a rest made longer is kept to its new end from when it started`() = runTest {
        val timers = timers()
        timers.startRest(30)
        run(5_000)
        timers.adjustRest(45)
        assertEquals(TimerRun.Rest(now, now + 45_000), keepAlive.runs.last())
        assertEquals(0, keepAlive.stops)
    }

    @Test
    fun `an EMOM started over a rest is kept with no gap`() = runTest {
        val timers = timers()
        timers.startRest(RestClock.DEFAULT_SECONDS)
        run(10_000)
        timers.startEmom(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        assertNull(timers.state.value.rest)
        assertEquals(TimerRun.Emom(now, 3), keepAlive.runs.last())
        assertEquals(0, keepAlive.stops)
        run(EmomClock(3).lengthMillis + EmomClock.DONE_HOLD_MILLIS)
        assertNull(timers.state.value.emom)
        assertEquals(EmomCue.Finished, beeper.cues.last())
        assertEquals(1, keepAlive.stops)
    }

    @Test
    fun `an EMOM logs each minute's set as it is done`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        timers.startEmom(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        run(EmomClock.COUNT_IN_MILLIS)
        timers.emomSetDone()
        run(EmomClock.MINUTE_MILLIS)
        timers.emomSetDone()
        assertEquals(listOf(Side.Left, Side.Right), repository.sessions.active.value?.sets?.map { it.side })
    }

    @Test
    fun `a hold logs its seconds and rests after it`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        val plank = SetDraft(exercise = BuiltInExercise.Plank, seconds = 20)
        val held = mutableListOf<SetDraft>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { timers.heldSets.collect { held += it } }
        timers.startHold(plank)
        assertEquals(TimerRun.Hold(now, 20), keepAlive.runs.last())
        run(HoldClock.COUNT_IN_MILLIS + 20_000)
        assertEquals(listOf(20), repository.sessions.active.value?.sets?.map { it.seconds })
        assertEquals(listOf(plank), held)
        // The rest follows the hold, kept with no gap between them.
        assertEquals(RestClock.DEFAULT_SECONDS, timers.state.value.rest?.seconds)
        assertEquals(TimerRun.Rest::class, keepAlive.runs.last()::class)
        assertEquals(0, keepAlive.stops)
    }

    @Test
    fun `stopping them all ends any run without logging and lets the keep-alive go`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        timers.startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20))
        run(HoldClock.COUNT_IN_MILLIS + 5_000)
        timers.stopAll()
        assertNull(timers.state.value.hold.run)
        assertEquals(emptyList(), repository.sessions.active.value?.sets.orEmpty())
        assertEquals(1, keepAlive.stops)
        timers.stopAll()
        assertEquals(1, keepAlive.stops)
    }

    @Test
    fun `a change of the wall clock bends no run`() = runTest {
        val timers = timers()
        timers.startRest(30)
        run(10_000)
        // An hour back, as a time zone or a manual change would.
        now -= 3_600_000
        run(10_000)
        assertEquals(RestClock(30).at(20_000 + 2 * SessionTimers.TICK_MILLIS), timers.state.value.restState)
    }

    @Test
    fun `with no run going nothing ticks`() = runTest {
        val timers = timers()
        timers.startRest(15)
        run(15_000)
        val beeps = beeper.cues.size
        val at = timers.state.value
        run(60_000)
        assertEquals(beeps, beeper.cues.size)
        assertEquals(at, timers.state.value)
    }

    @Test
    fun `the run going is kept on the session at each change and let go as it ends`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        timers.startRest(30)
        assertEquals(SavedTimer.Rest(now, 30), repository.sessions.active.value?.timer)
        timers.adjustRest(45)
        assertEquals(SavedTimer.Rest(now, 45), repository.sessions.active.value?.timer)
        timers.skipRest()
        assertNull(repository.sessions.active.value?.timer)
        timers.startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20))
        assertEquals(SavedTimer.Hold::class, repository.sessions.active.value?.timer?.let { it::class })
        timers.stopAll()
        assertNull(repository.sessions.active.value?.timer)
    }

    @Test
    fun `an EMOM is kept with the minutes done`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        timers.startEmom(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        run(EmomClock.COUNT_IN_MILLIS)
        timers.emomSetDone()
        val kept = repository.sessions.active.value?.timer as SavedTimer.Emom
        assertEquals(setOf(1), kept.done)
        assertEquals(BuiltInExercise.Press, kept.exercise)
    }

    @Test
    fun `a rest a killed process left goes on by the wall clock`() = runTest {
        repository.sessions.startSession()
        val started = now
        timers().apply { startRest(60) }.close()
        // The process is gone for twenty seconds; the next one's timers count from their own start.
        now += 20_000
        val timers = timers()
        timers.restore(repository.sessions.active.value?.timer ?: error("Kept no rest"))
        assertEquals(RestClock(60).at(20_000), timers.state.value.restState)
        assertEquals(TimerRun.Rest(started, started + 60_000), keepAlive.runs.last())
        run(40_000)
        assertNull(timers.state.value.rest)
        assertEquals(listOf(EmomCue.Countdown, EmomCue.Start), beeper.cues.takeLast(2))
        assertNull(repository.sessions.active.value?.timer)
    }

    @Test
    fun `an EMOM a killed process left goes on with its minutes done and no cue for the time away`() = runTest {
        repository.sessions.startSession()
        timers().apply {
            startEmom(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
            run(EmomClock.COUNT_IN_MILLIS)
            emomSetDone()
        }.close()
        val cues = beeper.cues.size
        // Back twenty seconds into the second minute.
        now += EmomClock.MINUTE_MILLIS + 20_000
        val timers = timers()
        timers.restore(repository.sessions.active.value?.timer ?: error("Kept no EMOM"))
        run(0)
        assertEquals(cues, beeper.cues.size)
        assertEquals(setOf(1), timers.state.value.emom?.done)
        timers.emomSetDone()
        assertEquals(listOf(Side.Left, Side.Right), repository.sessions.active.value?.sets?.map { it.side })
    }

    @Test
    fun `a run that ended while the process was gone is finished and not played out`() = runTest {
        repository.sessions.startSession()
        timers().apply { startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20)) }.close()
        val cues = beeper.cues.size
        val starts = keepAlive.runs.size
        now += 60_000
        val timers = timers()
        timers.restore(repository.sessions.active.value?.timer ?: error("Kept no hold"))
        run(0)
        assertNull(timers.state.value.hold.run)
        assertEquals(emptyList(), repository.sessions.active.value?.sets.orEmpty())
        assertEquals(cues, beeper.cues.size)
        assertEquals(starts, keepAlive.runs.size)
        assertNull(repository.sessions.active.value?.timer)
    }

    @Test
    fun `a run kept is not restored over one going`() = runTest {
        repository.sessions.startSession()
        val timers = timers()
        timers.startRest(30)
        timers.restore(SavedTimer.Rest(now - 10_000, 90))
        assertEquals(30, timers.state.value.rest?.seconds)
        assertEquals(SavedTimer.Rest(now, 30), repository.sessions.active.value?.timer)
    }

    @Test
    fun `a rest after a set says one fault of the lift then the set-up of the lift coming`() = runTest {
        val timers = timers()
        val snatch = BuiltInExercise.Snatch
        timers.upNext(ComingSet(BuiltInExercise.GobletSquat))
        timers.startRest(30, after = snatch)
        run(25_000)
        assertEquals(listOf(Coaching.Fault(snatch, 0), Coaching.Cue(BuiltInExercise.GobletSquat, 0)), voice.lines)
        timers.startRest(30, after = snatch)
        assertEquals(Coaching.Fault(snatch, 1), voice.lines.last())
        timers.stopAll()
    }

    @Test
    fun `a new bell coming says groove light in place of its set-up once for the lift and bell`() = runTest {
        val timers = timers()
        val swing = BuiltInExercise.OneHandSwing
        timers.upNext(ComingSet(swing, newBell = Load.Bell(24.0)))
        timers.startRest(30)
        run(25_000)
        assertEquals(listOf<Coaching>(Coaching.Safety(swing, SafetyLine.GrooveLight)), voice.lines)
        timers.startRest(30)
        run(25_000)
        assertEquals(Coaching.Cue(swing, 0), voice.lines.last())
        timers.stopAll()
    }

    @Test
    fun `a taxed day says stop a rep early once in place of a set-up cue after any groove`() = runTest {
        val timers = timers()
        val swing = BuiltInExercise.OneHandSwing
        timers.taxed(true)
        timers.upNext(ComingSet(swing, newBell = Load.Bell(24.0)))
        timers.opened()
        repeat(2) {
            timers.startRest(30)
            run(25_000)
        }
        assertEquals(
            listOf(
                Coaching.Safety(swing, SafetyLine.GrooveLight),
                Coaching.Safety(swing, SafetyLine.StopEarly),
                Coaching.Cue(swing, 0),
            ),
            voice.lines,
        )
        timers.stopAll()
    }

    @Test
    fun `a free session opening says no groove and a calm day nothing`() = runTest {
        val timers = timers()
        timers.upNext(ComingSet(BuiltInExercise.Snatch, newBell = Load.Bell(16.0)))
        timers.opened(grooving = false)
        timers.opened(grooving = false)
        assertEquals(emptyList<Coaching>(), voice.lines)
        timers.taxed(true)
        timers.opened(grooving = false)
        assertEquals(listOf<Coaching>(Coaching.Safety(BuiltInExercise.Snatch, SafetyLine.StopEarly)), voice.lines)
    }

    @Test
    fun `a density block logs its rounds and is kept by the platform to its cap`() = runTest {
        val timers = timers()
        repository.sessions.startSession()
        timers.startRest(90)
        timers.startDensity(BuiltInExercise.Snatch, Load.Bell(16.0), 5, minutes = 5, firstSide = Side.Left, best = 9)
        assertNull(timers.state.value.rest)
        assertEquals(TimerRun.Density(now, 5), keepAlive.runs.last())
        run(EmomClock.COUNT_IN_MILLIS)
        repeat(2) { timers.densityRoundDone() }
        assertEquals(listOf(Side.Left, Side.Right), repository.sessions.active.value?.sets?.map { it.side })
        assertEquals(
            SavedTimer.Density(now, BuiltInExercise.Snatch, Load.Bell(16.0), 5, 5, 2, Side.Left, best = 9),
            repository.sessions.active.value?.timer,
        )
        timers.stopDensity()
        assertNull(timers.state.value.density)
        assertNull(repository.sessions.active.value?.timer)
    }

    @Test
    fun `a density block a killed process left goes on with its rounds and one over is dropped`() = runTest {
        repository.sessions.startSession()
        timers().apply {
            startDensity(BuiltInExercise.Snatch, Load.Bell(16.0), 5, minutes = 5, firstSide = Side.Left, best = null)
            run(EmomClock.COUNT_IN_MILLIS)
            densityRoundDone()
        }.close()
        val kept = repository.sessions.active.value?.timer ?: error("Kept no density block")
        now += 2 * EmomClock.MINUTE_MILLIS
        val timers = timers()
        timers.restore(kept)
        run(0)
        assertEquals(1, timers.state.value.density?.rounds)
        timers.densityRoundDone()
        assertEquals(listOf(Side.Left, Side.Right), repository.sessions.active.value?.sets?.map { it.side })
        timers.stopAll()

        now += 10 * EmomClock.MINUTE_MILLIS
        val later = timers()
        later.restore(kept)
        run(0)
        assertNull(later.state.value.density)
        assertNull(repository.sessions.active.value?.timer)
    }
}
