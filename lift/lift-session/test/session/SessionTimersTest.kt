@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.HoldClock
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingKeepAlive
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

    /** Timers ticking on the test's scheduler, under its background scope, so none outlives the test. */
    private fun TestScope.timers() = SessionTimers(
        repository,
        WallClock { now },
        testScheduler.timeSource,
        beeper,
        keepAlive,
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
        timers.startEmom(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
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
        repository.startSession()
        timers.startEmom(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
        run(EmomClock.COUNT_IN_MILLIS)
        timers.emomSetDone()
        run(EmomClock.MINUTE_MILLIS)
        timers.emomSetDone()
        assertEquals(listOf(Side.Left, Side.Right), repository.active.value?.sets?.map { it.side })
    }

    @Test
    fun `a hold logs its seconds and rests after it`() = runTest {
        val timers = timers()
        repository.startSession()
        val plank = SetDraft(exercise = BuiltInExercise.Plank, seconds = 20)
        val held = mutableListOf<SetDraft>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { timers.heldSets.collect { held += it } }
        timers.startHold(plank)
        assertEquals(TimerRun.Hold(now, 20), keepAlive.runs.last())
        run(HoldClock.COUNT_IN_MILLIS + 20_000)
        assertEquals(listOf(20), repository.active.value?.sets?.map { it.seconds })
        assertEquals(listOf(plank), held)
        // The rest follows the hold, kept with no gap between them.
        assertEquals(RestClock.DEFAULT_SECONDS, timers.state.value.rest?.seconds)
        assertEquals(TimerRun.Rest::class, keepAlive.runs.last()::class)
        assertEquals(0, keepAlive.stops)
    }

    @Test
    fun `stopping them all ends any run without logging and lets the keep-alive go`() = runTest {
        val timers = timers()
        repository.startSession()
        timers.startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20))
        run(HoldClock.COUNT_IN_MILLIS + 5_000)
        timers.stopAll()
        assertNull(timers.state.value.hold.run)
        assertEquals(emptyList(), repository.active.value?.sets.orEmpty())
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
        repository.startSession()
        timers.startRest(30)
        assertEquals(SavedTimer.Rest(now, 30), repository.active.value?.timer)
        timers.adjustRest(45)
        assertEquals(SavedTimer.Rest(now, 45), repository.active.value?.timer)
        timers.skipRest()
        assertNull(repository.active.value?.timer)
        timers.startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20))
        assertEquals(SavedTimer.Hold::class, repository.active.value?.timer?.let { it::class })
        timers.stopAll()
        assertNull(repository.active.value?.timer)
    }

    @Test
    fun `an EMOM is kept with the minutes done`() = runTest {
        val timers = timers()
        repository.startSession()
        timers.startEmom(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
        run(EmomClock.COUNT_IN_MILLIS)
        timers.emomSetDone()
        val kept = repository.active.value?.timer as SavedTimer.Emom
        assertEquals(setOf(1), kept.done)
        assertEquals(BuiltInExercise.Press, kept.exercise)
    }

    @Test
    fun `a rest a killed process left goes on by the wall clock`() = runTest {
        repository.startSession()
        val started = now
        timers().apply { startRest(60) }.close()
        // The process is gone for twenty seconds; the next one's timers count from their own start.
        now += 20_000
        val timers = timers()
        timers.restore(repository.active.value?.timer ?: error("Kept no rest"))
        assertEquals(RestClock(60).at(20_000), timers.state.value.restState)
        assertEquals(TimerRun.Rest(started, started + 60_000), keepAlive.runs.last())
        run(40_000)
        assertNull(timers.state.value.rest)
        assertEquals(listOf(EmomCue.Countdown, EmomCue.Start), beeper.cues.takeLast(2))
        assertNull(repository.active.value?.timer)
    }

    @Test
    fun `an EMOM a killed process left goes on with its minutes done and no cue for the time away`() = runTest {
        repository.startSession()
        timers().apply {
            startEmom(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
            run(EmomClock.COUNT_IN_MILLIS)
            emomSetDone()
        }.close()
        val cues = beeper.cues.size
        // Back twenty seconds into the second minute.
        now += EmomClock.MINUTE_MILLIS + 20_000
        val timers = timers()
        timers.restore(repository.active.value?.timer ?: error("Kept no EMOM"))
        run(0)
        assertEquals(cues, beeper.cues.size)
        assertEquals(setOf(1), timers.state.value.emom?.done)
        timers.emomSetDone()
        assertEquals(listOf(Side.Left, Side.Right), repository.active.value?.sets?.map { it.side })
    }

    @Test
    fun `a run that ended while the process was gone is finished and not played out`() = runTest {
        repository.startSession()
        timers().apply { startHold(SetDraft(exercise = BuiltInExercise.Plank, seconds = 20)) }.close()
        val cues = beeper.cues.size
        val starts = keepAlive.runs.size
        now += 60_000
        val timers = timers()
        timers.restore(repository.active.value?.timer ?: error("Kept no hold"))
        run(0)
        assertNull(timers.state.value.hold.run)
        assertEquals(emptyList(), repository.active.value?.sets.orEmpty())
        assertEquals(cues, beeper.cues.size)
        assertEquals(starts, keepAlive.runs.size)
        assertNull(repository.active.value?.timer)
    }

    @Test
    fun `a run kept is not restored over one going`() = runTest {
        repository.startSession()
        val timers = timers()
        timers.startRest(30)
        timers.restore(SavedTimer.Rest(now - 10_000, 90))
        assertEquals(30, timers.state.value.rest?.seconds)
        assertEquals(SavedTimer.Rest(now, 30), repository.active.value?.timer)
    }
}
