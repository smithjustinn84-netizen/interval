@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.HoldClock
import dev.justinsmith.lift.domain.RestClock
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
}
