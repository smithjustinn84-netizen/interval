@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.OutlinedInterval
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.WorkoutOutline
import dev.justinsmith.interval.timer.estimateCalories
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

class WorkoutSessionTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val lifts = FakeLiftStorage().repository()
    private val player = RecordingCuePlayer()
    private val health = RecordingHealthStore(HealthStatus.Connected)

    /** The platform's keeper in this process; a new one after [killProcess]. */
    private var background = RecordingBackgroundWorkout()

    /** The wall clock the session saves its progress by. Tests move it to stand for time the process was gone. */
    private var now = 7L
    private val tickers = mutableListOf<WorkoutTicker>()

    @BeforeTest
    fun setUp() {
        repository.settings.updateConfig {
            IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2)
        }
    }

    // Stops every ticker, so none outlives its test on the shared scheduler.
    @AfterTest
    fun closeTickers() = tickers.forEach(WorkoutTicker::close)

    /** The process's session over [repository], timed by the test's clock unless [timeSource] says otherwise. */
    private fun TestScope.workoutSession(
        repository: IntervalRepository = this@WorkoutSessionTest.repository,
        timeSource: TimeSource.WithComparableMarks = testScheduler.timeSource,
    ): WorkoutSession {
        val ticker = WorkoutTicker(timeSource, tickerScope()).also { tickers += it }
        val recorder = with(repository) {
            WorkoutRecorder(history, presets, flows, settings, lifts.sessions, FixedTimestampFormatter, { now }, health)
        }
        return with(repository) {
            WorkoutSession(settings, settings, player, { now }, background, recorder, ticker, flows)
        }
    }

    /**
     * A session with the Workout page [id] open on it. Two with the same [id] are one workout before and after the
     * process was killed.
     */
    private fun TestScope.openWorkout(
        repository: IntervalRepository = this@WorkoutSessionTest.repository,
        id: Long = WORKOUT_ID,
        timeSource: TimeSource.WithComparableMarks = testScheduler.timeSource,
    ) = workoutSession(repository, timeSource).apply { open(id) }

    /**
     * Where a ticker ticks: on the test's scheduler, under its background scope, so a workout still running as the
     * test ends stops with it rather than ticking on through the test's last wait.
     */
    private fun TestScope.tickerScope() =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    /** Runs the ticker for [millis] of the scheduler's time. */
    private fun TestScope.run(millis: Long) {
        advanceTimeBy(millis + WorkoutTicker.TICK_MILLIS)
        runCurrent()
    }

    /** One tick after [away] has passed on [time] while the scheduler slept: the app suspended, then back. */
    private fun TestScope.returnAfter(time: TestTimeSource, away: Duration) {
        time += away
        advanceTimeBy(WorkoutTicker.TICK_MILLIS)
        runCurrent()
    }

    /** The process dies with its session in it: its ticker stops, its keeper goes, and nothing is cleared. */
    private fun killProcess() {
        tickers.forEach(WorkoutTicker::close)
        background = RecordingBackgroundWorkout()
        player.spoken.clear()
    }

    /** Every status [session] shows off screen from now on, in order. */
    private fun TestScope.statusesOf(session: WorkoutSession): List<WorkoutStatus> {
        val statuses = mutableListOf<WorkoutStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.status.filterNotNull().collect { statuses += it }
        }
        return statuses
    }

    private val WorkoutSession.ui: WorkoutUiState get() = checkNotNull(state.value).toUiState(emptyList(), emptyList())

    private fun WorkoutSession.pause() {
        if (ui.running) togglePause()
    }

    private fun WorkoutSession.resume() {
        if (!ui.running) togglePause()
    }

    @Test
    fun `a workout runs to done cues each phase and is recorded`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        assertEquals(Phase.Work, session.ui.phase)

        advanceTimeBy(15_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()

        val state = session.ui
        assertTrue(state.isDone)
        assertFalse(state.running)
        assertEquals(2, state.rounds)
        assertEquals("0:15", state.total)
        val countdown = listOf(Speech.Countdown(3), Speech.Countdown(2), Speech.Countdown(1))
        assertEquals(
            listOf(Speech.Work(lastRound = false)) + countdown + Speech.Rest + countdown +
                Speech.Work(lastRound = true) + countdown + Speech.Finished,
            player.spoken,
        )
        assertEquals(listOf(Haptic.Work, Haptic.Rest, Haptic.Work, Haptic.Finished), player.haptics)
        assertEquals("0:15", state.elapsed)
        val entry = repository.history.history.value.single()
        assertEquals(7L, entry.finishedAtEpochMillis)
        assertEquals(15, entry.elapsedSeconds)
        assertFalse(entry.endedEarly)
    }

    @Test
    fun `a finished workout goes to the health store with its course`() = runTest(dispatcher) {
        repository.settings.updateAppSettings { it.copy(body = BodySettings(weightKg = 80.0, effort = Effort.Hard)) }
        now = 1_000
        val session = openWorkout()
        now = 6_000
        run(5_000)
        now = 11_000
        run(5_000)
        now = 16_000
        run(5_000)
        assertTrue(session.ui.isDone)

        val workout = health.workouts.single()
        assertEquals("interval-1000", workout.id)
        assertEquals(HealthWorkoutKind.Intervals, workout.kind)
        assertNull(workout.title)
        assertEquals(1_000, workout.startedAtEpochMillis)
        assertEquals(16_000, workout.endedAtEpochMillis)
        assertEquals(estimateCalories(10, 5, 80.0, Effort.Hard), workout.calories)
        assertEquals(
            listOf(
                HealthSegment(1_000, 6_000, HealthMove.Work),
                HealthSegment(6_000, 11_000, HealthMove.Rest),
                HealthSegment(11_000, 16_000, HealthMove.Work),
            ),
            workout.segments,
        )
    }

    @Test
    fun `a pause is a stretch of the course and ending early closes it`() = runTest(dispatcher) {
        now = 1_000
        val session = openWorkout()
        now = 3_000
        session.pause()
        now = 4_000
        session.resume()
        now = 7_000
        run(5_000)
        now = 9_000
        session.end()

        val workout = health.workouts.single()
        assertEquals(9_000, workout.endedAtEpochMillis)
        assertEquals(
            listOf(
                HealthSegment(1_000, 3_000, HealthMove.Work),
                HealthSegment(3_000, 4_000, HealthMove.Pause),
                HealthSegment(4_000, 7_000, HealthMove.Work),
                HealthSegment(7_000, 9_000, HealthMove.Rest),
            ),
            workout.segments,
        )
    }

    @Test
    fun `the course survives the process dying and is whole when the workout finishes`() = runTest(dispatcher) {
        now = 1_000
        openWorkout()
        now = 6_000
        run(5_000)
        killProcess()

        now = 11_000
        val restored = openWorkout()
        now = 16_000
        run(5_000)
        assertTrue(restored.ui.isDone)

        val workout = health.workouts.single()
        assertEquals("interval-1000", workout.id)
        assertEquals(
            listOf(
                HealthSegment(1_000, 6_000, HealthMove.Work),
                HealthSegment(6_000, 11_000, HealthMove.Rest),
                HealthSegment(11_000, 16_000, HealthMove.Work),
            ),
            workout.segments,
        )
    }

    @Test
    fun `nothing goes to the health store while it is not connected`() = runTest(dispatcher) {
        health.status.value = HealthStatus.Disconnected
        openWorkout()
        run(15_000)
        assertEquals(1, repository.history.history.value.size)
        assertTrue(health.workouts.isEmpty())
    }

    @Test
    fun `time spent counts only the time the timer ran`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        advanceTimeBy(2_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()
        session.pause()
        advanceTimeBy(10_000)
        session.resume()
        // Skipping the rest of the work interval adds nothing.
        session.skip()

        session.end()
        assertEquals("0:02", session.ui.elapsed)
        assertEquals(1, session.ui.completedRounds)
        val entry = repository.history.history.value.single()
        assertEquals(2, entry.elapsedSeconds)
        assertTrue(entry.endedEarly)
    }

    @Test
    fun `back returns to the interval before and add time extends this one without cues`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        session.pause()
        session.skip()
        assertEquals(Phase.Rest, session.ui.phase)
        val cuesBefore = player.spoken.size

        session.back()
        assertEquals(Phase.Work, session.ui.phase)
        assertEquals("0:05", session.ui.clock)

        session.addTime()
        assertEquals("0:15", session.ui.clock)
        assertEquals(cuesBefore, player.spoken.size)
    }

    @Test
    fun `pausing freezes the clock`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        advanceTimeBy(2_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()
        session.pause()
        val paused = session.ui
        assertTrue(paused.paused)

        advanceTimeBy(10_000)
        assertEquals(paused.clock, session.ui.clock)

        session.resume()
        assertTrue(session.ui.running)
    }

    @Test
    fun `cues respect the user's toggles`() = runTest(dispatcher) {
        repository.settings.updateCueSettings { CueSettings(sound = false, voice = false, vibrate = false) }
        val session = openWorkout(repository)
        session.skip()
        session.skip()
        session.skip()
        assertTrue(session.ui.isDone)
        assertTrue(player.beeps.isEmpty() && player.spoken.isEmpty() && player.haptics.isEmpty())
    }

    @Test
    fun `restart begins again from the first interval`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        session.skip()
        session.skip()
        session.restart()
        val state = session.ui
        assertEquals(Phase.Work, state.phase)
        assertEquals(1, state.round)
        assertEquals("0:15", state.sessionLeft)
        assertEquals(NextUp.Rest("0:05"), state.next)
    }

    @Test
    fun `ending early records the time spent and shows the summary`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        advanceTimeBy(6_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()
        assertTrue(session.ui.hasProgress)

        session.end()
        val state = session.ui
        assertTrue(state.isDone && state.ended)
        assertFalse(state.running)
        assertEquals(1, state.completedRounds)
        assertEquals("0:06", state.elapsed)
        assertEquals(6, repository.history.history.value.single().elapsedSeconds)
        assertTrue(repository.history.history.value.single().endedEarly)

        session.togglePause()
        assertFalse(session.ui.running)
    }

    @Test
    fun `work time counts only work intervals and gives the summary its calories`() = runTest(dispatcher) {
        repository.settings.updateAppSettings { it.copy(body = BodySettings(weightKg = 80.0, effort = Effort.Hard)) }
        val session = openWorkout(repository)
        advanceTimeBy(15_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()

        val entry = repository.history.history.value.single()
        assertEquals(10, entry.workActiveSeconds)
        assertEquals(estimateCalories(10, 5, 80.0, Effort.Hard), session.ui.calories)
    }

    @Test
    fun `a skipped work interval adds no work time`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        session.skip()
        advanceTimeBy(10_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()

        val entry = repository.history.history.value.single()
        assertEquals(10, entry.elapsedSeconds)
        assertEquals(5, entry.workActiveSeconds)
    }

    @Test
    fun `the summary has no calories without a weight`() = runTest(dispatcher) {
        repository.settings.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        val session = openWorkout(repository)
        advanceTimeBy(15_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()
        assertNull(session.ui.calories)
    }

    @Test
    fun `the controls do nothing once the workout has ended early`() = runTest(dispatcher) {
        val session = openWorkout(repository)
        advanceTimeBy(6_000 + WorkoutTicker.TICK_MILLIS)
        runCurrent()
        session.end()
        val ended = session.state.value

        repeat(5) { session.skip() }
        session.back()
        session.addTime()
        session.togglePause()

        assertEquals(ended, session.state.value)
        assertEquals(1, repository.history.history.value.size)
    }

    @Test
    fun `time away counts in full and the workout is as far on as the clock says`() = runTest(dispatcher) {
        val time = TestTimeSource()
        val session = openWorkout(timeSource = time)
        returnAfter(time, 2.seconds)
        assertEquals("0:03", session.ui.clock)

        // Seven seconds away: the work interval's last three, then four of the five-second rest.
        returnAfter(time, 7.seconds)
        val state = session.ui
        assertEquals(Phase.Rest, state.phase)
        assertEquals("0:01", state.clock)
        assertEquals(1, state.round)
        assertTrue(state.running)
        assertEquals("0:09", state.elapsed)
        // Heard: where it landed and that interval's seconds left, not every cue the time away covered.
        assertEquals(
            listOf(Speech.Work(lastRound = false), Speech.Countdown(3), Speech.Rest, Speech.Countdown(1)),
            player.spoken,
        )
    }

    @Test
    fun `time away past the end finishes the workout with one cue and records it`() = runTest(dispatcher) {
        val time = TestTimeSource()
        val session = openWorkout(timeSource = time)
        returnAfter(time, 40.seconds)

        val state = session.ui
        assertTrue(state.isDone)
        assertEquals("0:15", state.elapsed)
        assertEquals(listOf(Speech.Work(lastRound = false), Speech.Finished), player.spoken)
        val entry = repository.history.history.value.single()
        assertEquals(15, entry.elapsedSeconds)
        assertEquals(10, entry.workActiveSeconds)
        assertFalse(background.kept)
    }

    @Test
    fun `after the process died a running workout picks up where it has got to`() = runTest(dispatcher) {
        now = 1_000
        openWorkout()
        run(2_000)
        killProcess()

        now += 7_000
        val restored = openWorkout()
        val state = restored.ui
        assertEquals(Phase.Rest, state.phase)
        assertEquals("0:03", state.clock)
        assertEquals(1, state.round)
        assertTrue(state.running)
        assertEquals("0:07", state.elapsed)
        // Heard: the phase it landed in and the seconds left in it, nothing from the time in between.
        assertEquals(listOf(Speech.Rest, Speech.Countdown(3)), player.spoken)
        assertTrue(background.kept)

        run(3_000)
        assertEquals(Phase.Work, restored.ui.phase)
        assertEquals(2, restored.ui.round)
    }

    @Test
    fun `after the process died a paused workout stays where it was paused`() = runTest(dispatcher) {
        val first = openWorkout()
        run(2_000)
        first.pause()
        killProcess()

        now += 60_000
        val restored = openWorkout()
        advanceTimeBy(10_000)
        val state = restored.ui
        assertEquals(Phase.Work, state.phase)
        assertEquals("0:03", state.clock)
        assertTrue(state.paused)
        assertEquals("0:02", state.elapsed)
        assertTrue(player.spoken.isEmpty())
        assertEquals(
            WorkoutStatus(Phase.Work, paused = true, clock = "0:03", round = 1, rounds = 2, outline(elapsed = 2)),
            restored.status.value,
        )

        restored.resume()
        run(1_000)
        assertEquals("0:02", restored.ui.clock)
    }

    @Test
    fun `after the process died a finished workout shows its summary and is not recorded again`() = runTest(
        dispatcher,
    ) {
        val first = openWorkout()
        run(15_000)
        assertTrue(first.ui.isDone)
        val week = first.ui.week
        killProcess()

        now += 60_000
        val restored = openWorkout()
        val state = restored.ui
        assertTrue(state.isDone)
        assertFalse(state.ended)
        assertEquals("0:15", state.elapsed)
        assertEquals(week, state.week)
        assertEquals(1, repository.history.history.value.size)
        assertTrue(player.spoken.isEmpty())
        assertEquals(0, background.starts)
    }

    @Test
    fun `after the process died a workout ended early shows that summary`() = runTest(dispatcher) {
        val first = openWorkout()
        run(6_000)
        first.end()
        val week = first.ui.week
        killProcess()

        val restored = openWorkout()
        val state = restored.ui
        assertTrue(state.isDone && state.ended)
        assertEquals(1, state.completedRounds)
        assertEquals("0:06", state.elapsed)
        assertEquals(week, state.week)
        assertEquals(1, repository.history.history.value.size)
    }

    @Test
    fun `a workout started afresh ignores the progress an older one left behind`() = runTest(dispatcher) {
        openWorkout()
        run(6_000)
        killProcess()

        now += 60_000
        val fresh = openWorkout(id = WORKOUT_ID + 1)
        val state = fresh.ui
        assertEquals(Phase.Work, state.phase)
        assertEquals(1, state.round)
        assertEquals("0:05", state.clock)
        assertEquals("0:00", state.elapsed)
    }

    @Test
    fun `the workout held is shown as it stands when its page opens it again`() = runTest(dispatcher) {
        val session = openWorkout()
        run(2_000)

        session.open(WORKOUT_ID)
        assertEquals("0:02", session.ui.elapsed)
        assertTrue(session.ui.running)
        assertEquals(1, background.starts)
        assertEquals(1, player.spoken.count { it is Speech.Work })
    }

    @Test
    fun `a new workout ends the one held before`() = runTest(dispatcher) {
        val session = openWorkout()
        run(2_000)

        session.open(WORKOUT_ID + 1)
        assertEquals(WORKOUT_ID + 1, session.state.value?.id)
        assertEquals("0:00", session.ui.elapsed)
        assertEquals(WORKOUT_ID + 1, repository.settings.workoutProgress.value?.id)
        assertEquals(1, background.stops)
        assertTrue(background.kept)
        assertTrue(repository.history.history.value.isEmpty())
    }

    @Test
    fun `leaving clears the saved progress and the notification`() = runTest(dispatcher) {
        val session = openWorkout()
        run(2_000)
        assertEquals(WORKOUT_ID, repository.settings.workoutProgress.value?.id)
        assertTrue(background.kept)

        session.leave()
        assertNull(session.state.value)
        assertNull(session.status.value)
        assertNull(repository.settings.workoutProgress.value)
        assertFalse(background.kept)

        // Nothing ticks on: the workout has gone.
        run(15_000)
        assertTrue(repository.history.history.value.isEmpty())
    }

    @Test
    fun `the notification follows the workout and hears of each change once`() = runTest(dispatcher) {
        val session = workoutSession()
        val statuses = statusesOf(session)
        session.open(WORKOUT_ID)
        assertEquals(1, background.starts)
        assertEquals(
            listOf(WorkoutStatus(Phase.Work, paused = false, clock = "0:05", round = 1, rounds = 2, outline(0))),
            statuses,
        )

        run(1_000)
        assertEquals(listOf("0:05", "0:04"), statuses.map { it.clock })
        assertEquals(listOf(0, 1), statuses.map { it.outline.elapsedSeconds })

        session.pause()
        assertTrue(statuses.last().paused)
        session.resume()
        assertFalse(statuses.last().paused)

        run(15_000)
        assertTrue(session.ui.isDone)
        assertFalse(background.kept)
        assertNull(session.status.value)
        assertEquals(1, background.stops)

        session.restart()
        assertEquals(2, background.starts)
        assertTrue(background.kept)
    }

    @Test
    fun `the notification's buttons pause and resume the workout`() = runTest(dispatcher) {
        val session = openWorkout()
        val controls: ActiveWorkout = session
        controls.togglePause()
        assertTrue(session.ui.paused)
        assertTrue(session.status.value?.paused == true)
        controls.togglePause()
        assertTrue(session.ui.running)
    }

    @Test
    fun `the changes ahead are read from where the workout stands and stop with it`() = runTest(dispatcher) {
        val session = openWorkout()
        val controls: ActiveWorkout = session
        // run() ticks once past the time asked for: 2.1 s of the first 5 s work interval are gone.
        run(2_000)
        assertEquals(
            listOf(
                UpcomingChange(Phase.Rest, round = 1, offsetMillis = 2_900),
                UpcomingChange(Phase.Work, round = 2, offsetMillis = 7_900),
                UpcomingChange(Phase.Done, round = 2, offsetMillis = 12_900),
            ),
            controls.upcomingChanges(),
        )

        session.pause()
        assertTrue(controls.upcomingChanges().isEmpty())
        session.resume()
        assertEquals(3, controls.upcomingChanges().size)

        run(15_000)
        assertTrue(session.ui.isDone)
        assertTrue(controls.upcomingChanges().isEmpty())
    }

    @Test
    fun `End from the notification with a round done records and shows the summary`() = runTest(dispatcher) {
        val session = openWorkout()
        run(6_000)
        (session as ActiveWorkout).end()

        val state = session.ui
        assertTrue(state.isDone && state.ended)
        assertFalse(state.leaving)
        assertEquals(1, repository.history.history.value.size)
        assertFalse(background.kept)
    }

    @Test
    fun `End from the notification with no round done leaves with nothing kept`() = runTest(dispatcher) {
        val session = openWorkout()
        run(2_000)
        (session as ActiveWorkout).end()

        val state = session.ui
        assertTrue(state.leaving)
        assertFalse(state.running)
        assertTrue(repository.history.history.value.isEmpty())
        assertFalse(background.kept)
    }

    @Test
    fun `End in the cool-down finishes the workout`() = runTest(dispatcher) {
        repository.settings.updateConfig { it.copy(coolDownSeconds = 60) }
        val session = openWorkout()
        repeat(3) { session.skip() }
        assertEquals(Phase.CoolDown, session.ui.phase)

        session.end()
        assertTrue(session.ui.isDone)
        assertFalse(session.ui.ended)
        assertFalse(repository.history.history.value.single().endedEarly)
    }

    @Test
    fun `what the summary says is kept with the workout in History and saved with it`() = runTest(dispatcher) {
        val session = openWorkout()
        run(15_000)

        session.reflect(PerceivedEffort.Hard, "  Felt strong ")
        runCurrent()

        assertEquals(PerceivedEffort.Hard, session.ui.effort)
        assertEquals("  Felt strong ", session.ui.note)
        val entry = repository.history.history.value.single()
        assertEquals(PerceivedEffort.Hard, entry.effort)
        assertEquals("Felt strong", entry.note)
        assertEquals(PerceivedEffort.Hard, repository.settings.workoutProgress.value?.effort)
    }

    @Test
    fun `the health store is written again with the summary's words as it goes`() = runTest(dispatcher) {
        val session = openWorkout()
        run(15_000)
        assertEquals(1, health.workouts.size)

        session.reflect(PerceivedEffort.Hard, "")
        session.describeForHealth("Hard · Felt strong")
        now = 99L
        session.leave()

        assertEquals(2, health.workouts.size)
        val again = health.workouts.last()
        assertEquals(health.workouts.first().id, again.id)
        assertEquals("Hard · Felt strong", again.notes)
        assertEquals(99L, again.version)
        assertEquals(health.workouts.first().segments, again.segments)
    }

    @Test
    fun `with nothing said of it the health store is not written again`() = runTest(dispatcher) {
        val session = openWorkout()
        run(15_000)
        session.leave()
        assertEquals(1, health.workouts.size)
    }

    /** The workout these tests run, 5 s of work, 5 of rest and 5 of work, [elapsed] seconds in. */
    private fun outline(elapsed: Int) = WorkoutOutline(
        listOf(
            OutlinedInterval(Phase.Work, round = 1, seconds = 5),
            OutlinedInterval(Phase.Rest, round = 1, seconds = 5),
            OutlinedInterval(Phase.Work, round = 2, seconds = 5),
        ),
        elapsed,
    )
}

internal const val WORKOUT_ID = 1_700_000_000_000L
