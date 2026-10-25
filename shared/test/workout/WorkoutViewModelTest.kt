@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeLiftStorage
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.estimateCalories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

class WorkoutViewModelTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val lifts = FakeLiftStorage().repository()
    private val player = RecordingCuePlayer()
    private val background = RecordingBackgroundWorkout()

    /** The wall clock the ViewModel saves its progress by. Tests move it to stand for time the process was gone. */
    private var now = 7L
    private val viewModels = mutableListOf<WorkoutViewModel>()

    @BeforeTest
    fun setUp() {
        repository.updateConfig { IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2) }
    }

    // Stops every ticker, so none outlives its test on the shared scheduler.
    @AfterTest
    fun clearViewModels() = viewModels.forEach { it.viewModelScope.cancel() }

    /**
     * A workout over [repository], timed by the test's clock unless [timeSource] says otherwise, stopped once the
     * test ends. Two with the same [id] are one workout before and after the process was killed.
     */
    private fun TestScope.workoutViewModel(
        repository: IntervalRepository = this@WorkoutViewModelTest.repository,
        id: Long = WORKOUT_ID,
        timeSource: TimeSource.WithComparableMarks = testScheduler.timeSource,
    ) = WorkoutViewModel(id, repository, player, lifts, FixedTimestampFormatter, { now }, background, timeSource)
        .also { viewModels += it }

    /** Runs the ticker for [millis] of the scheduler's time. */
    private fun TestScope.run(millis: Long) {
        advanceTimeBy(millis + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
    }

    /** One tick after [away] has passed on [time] while the scheduler slept: the app suspended, then back. */
    private fun TestScope.returnAfter(time: TestTimeSource, away: Duration) {
        time += away
        advanceTimeBy(WorkoutViewModel.TICK_MILLIS)
        runCurrent()
    }

    /** The process dies with [viewModel] in it: its coroutines stop, and nothing is cleared. */
    private fun killProcess(viewModel: WorkoutViewModel) {
        viewModel.viewModelScope.cancel()
        player.spoken.clear()
    }

    /** The screen leaves for good: the ViewModel is cleared, as its back-stack entry's store clears it. */
    private fun leave(viewModel: WorkoutViewModel) {
        ViewModelStore().apply { put("workout", viewModel) }.clear()
    }

    @Test
    fun `a workout runs to done cues each phase and is recorded`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        assertEquals(Phase.Work, viewModel.state.value.phase)

        advanceTimeBy(15_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()

        val state = viewModel.state.value
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
        val entry = repository.history.value.single()
        assertEquals(7L, entry.finishedAtEpochMillis)
        assertEquals(15, entry.elapsedSeconds)
        assertFalse(entry.endedEarly)
    }

    @Test
    fun `time spent counts only the time the timer ran`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(2_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        viewModel.pause()
        advanceTimeBy(10_000)
        viewModel.resume()
        // Skipping the rest of the work interval adds nothing.
        viewModel.skip()

        viewModel.endEarly()
        assertEquals("0:02", viewModel.state.value.elapsed)
        assertEquals(1, viewModel.state.value.completedRounds)
        val entry = repository.history.value.single()
        assertEquals(2, entry.elapsedSeconds)
        assertTrue(entry.endedEarly)
    }

    @Test
    fun `back returns to the interval before and add time extends this one without cues`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        viewModel.pause()
        viewModel.skip()
        assertEquals(Phase.Rest, viewModel.state.value.phase)
        val cuesBefore = player.spoken.size

        viewModel.back()
        assertEquals(Phase.Work, viewModel.state.value.phase)
        assertEquals("0:05", viewModel.state.value.clock)

        viewModel.addTime()
        assertEquals("0:15", viewModel.state.value.clock)
        assertEquals(cuesBefore, player.spoken.size)
    }

    @Test
    fun `pausing freezes the clock`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(2_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        viewModel.pause()
        val paused = viewModel.state.value
        assertTrue(paused.paused)

        advanceTimeBy(10_000)
        assertEquals(paused.clock, viewModel.state.value.clock)

        viewModel.resume()
        assertTrue(viewModel.state.value.running)
        viewModel.pause()
    }

    @Test
    fun `cues respect the user's toggles`() = runTest(dispatcher) {
        repository.updateCueSettings { CueSettings(sound = false, voice = false, vibrate = false) }
        val viewModel = workoutViewModel(repository)
        viewModel.skip()
        viewModel.skip()
        viewModel.skip()
        assertTrue(viewModel.state.value.isDone)
        assertTrue(player.beeps.isEmpty() && player.spoken.isEmpty() && player.haptics.isEmpty())
    }

    @Test
    fun `restart begins again from the first interval`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        viewModel.skip()
        viewModel.skip()
        viewModel.restart()
        val state = viewModel.state.value
        assertEquals(Phase.Work, state.phase)
        assertEquals(1, state.round)
        assertEquals("0:15", state.sessionLeft)
        assertEquals(NextUp.Rest("0:05"), state.next)
        viewModel.pause()
    }

    @Test
    fun `ending early records the time spent and shows the summary`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(6_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        assertTrue(viewModel.state.value.hasProgress)

        viewModel.endEarly()
        val state = viewModel.state.value
        assertTrue(state.isDone && state.ended)
        assertFalse(state.running)
        assertEquals(1, state.completedRounds)
        assertEquals("0:06", state.elapsed)
        assertEquals(6, repository.history.value.single().elapsedSeconds)
        assertTrue(repository.history.value.single().endedEarly)

        viewModel.resume()
        assertFalse(viewModel.state.value.running)
    }

    @Test
    fun `work time counts only work intervals and gives the summary its calories`() = runTest(dispatcher) {
        repository.updateAppSettings { it.copy(body = BodySettings(weightKg = 80.0, effort = Effort.Hard)) }
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(15_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()

        val entry = repository.history.value.single()
        assertEquals(10, entry.workActiveSeconds)
        assertEquals(estimateCalories(10, 5, 80.0, Effort.Hard), viewModel.state.value.calories)
    }

    @Test
    fun `a skipped work interval adds no work time`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        viewModel.skip()
        advanceTimeBy(10_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()

        val entry = repository.history.value.single()
        assertEquals(10, entry.elapsedSeconds)
        assertEquals(5, entry.workActiveSeconds)
    }

    @Test
    fun `the summary has no calories without a weight`() = runTest(dispatcher) {
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(15_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        assertNull(viewModel.state.value.calories)
    }

    @Test
    fun `a summary offers saving only a shape no preset has`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        repeat(3) { viewModel.skip() }
        assertEquals("0:00", viewModel.state.value.elapsed)
        assertEquals(0, repository.history.value.single().elapsedSeconds)
        assertTrue(viewModel.state.value.canSavePreset)
        assertNull(viewModel.state.value.presetName)
        viewModel.saveAsPreset(Preset("Short", viewModel.state.value.config))
        runCurrent()
        assertFalse(viewModel.state.value.canSavePreset)
        assertEquals("Short", viewModel.state.value.presetName)
    }

    @Test
    fun `a preset saved from the summary shows there once its write lands`() = runTest(dispatcher) {
        val repository = FakeStorage().repository(scope = deferredStorageScope())
        runCurrent()
        repository.updateConfig { IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2) }
        val viewModel = workoutViewModel(repository)
        repeat(3) { viewModel.skip() }
        assertTrue(viewModel.state.value.canSavePreset)

        viewModel.saveAsPreset(Preset("Short", viewModel.state.value.config))
        advanceUntilIdle()

        assertEquals("Short", viewModel.state.value.presetName)
        assertFalse(viewModel.state.value.canSavePreset)
    }

    @Test
    fun `a preset saved from the summary goes on the Timer too`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        repeat(3) { viewModel.skip() }
        val reshaped = viewModel.state.value.config.copy(workSeconds = 50)
        viewModel.saveAsPreset(Preset("Longer", reshaped))
        assertEquals(50, repository.config.value.workSeconds)
    }

    @Test
    fun `the controls do nothing once the workout has ended early`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(repository)
        advanceTimeBy(6_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        viewModel.endEarly()
        val ended = viewModel.state.value

        repeat(5) { viewModel.skip() }
        viewModel.back()
        viewModel.addTime()

        assertEquals(ended, viewModel.state.value)
        assertEquals(1, repository.history.value.size)
    }

    @Test
    fun `time away counts in full and the workout is as far on as the clock says`() = runTest(dispatcher) {
        val time = TestTimeSource()
        val viewModel = workoutViewModel(timeSource = time)
        returnAfter(time, 2.seconds)
        assertEquals("0:03", viewModel.state.value.clock)

        // Seven seconds away: the work interval's last three, then four of the five-second rest.
        returnAfter(time, 7.seconds)
        val state = viewModel.state.value
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
        viewModel.pause()
    }

    @Test
    fun `time away past the end finishes the workout with one cue and records it`() = runTest(dispatcher) {
        val time = TestTimeSource()
        val viewModel = workoutViewModel(timeSource = time)
        returnAfter(time, 40.seconds)

        val state = viewModel.state.value
        assertTrue(state.isDone)
        assertEquals("0:15", state.elapsed)
        assertEquals(listOf(Speech.Work(lastRound = false), Speech.Finished), player.spoken)
        val entry = repository.history.value.single()
        assertEquals(15, entry.elapsedSeconds)
        assertEquals(10, entry.workActiveSeconds)
        assertFalse(background.kept)
    }

    @Test
    fun `after the process died a running workout picks up where it has got to`() = runTest(dispatcher) {
        now = 1_000
        val first = workoutViewModel()
        run(2_000)
        killProcess(first)

        now += 7_000
        val restored = workoutViewModel()
        val state = restored.state.value
        assertEquals(Phase.Rest, state.phase)
        assertEquals("0:03", state.clock)
        assertEquals(1, state.round)
        assertTrue(state.running)
        assertEquals("0:07", state.elapsed)
        // Heard: the phase it landed in and the seconds left in it, nothing from the time in between.
        assertEquals(listOf(Speech.Rest, Speech.Countdown(3)), player.spoken)
        assertTrue(background.kept)

        run(3_000)
        assertEquals(Phase.Work, restored.state.value.phase)
        assertEquals(2, restored.state.value.round)
        restored.pause()
    }

    @Test
    fun `after the process died a paused workout stays where it was paused`() = runTest(dispatcher) {
        val first = workoutViewModel()
        run(2_000)
        first.pause()
        killProcess(first)

        now += 60_000
        val restored = workoutViewModel()
        advanceTimeBy(10_000)
        val state = restored.state.value
        assertEquals(Phase.Work, state.phase)
        assertEquals("0:03", state.clock)
        assertTrue(state.paused)
        assertEquals("0:02", state.elapsed)
        assertTrue(player.spoken.isEmpty())
        assertEquals(
            WorkoutStatus(Phase.Work, paused = true, clock = "0:03", round = 1, rounds = 2),
            background.statuses.last(),
        )

        restored.resume()
        run(1_000)
        assertEquals("0:02", restored.state.value.clock)
        restored.pause()
    }

    @Test
    fun `after the process died a finished workout shows its summary and is not recorded again`() = runTest(
        dispatcher,
    ) {
        val first = workoutViewModel()
        run(15_000)
        assertTrue(first.state.value.isDone)
        killProcess(first)
        val starts = background.starts

        now += 60_000
        val restored = workoutViewModel()
        val state = restored.state.value
        assertTrue(state.isDone)
        assertFalse(state.ended)
        assertEquals("0:15", state.elapsed)
        assertEquals(first.state.value.week, state.week)
        assertEquals(1, repository.history.value.size)
        assertTrue(player.spoken.isEmpty())
        assertEquals(starts, background.starts)
    }

    @Test
    fun `after the process died a workout ended early shows that summary`() = runTest(dispatcher) {
        val first = workoutViewModel()
        run(6_000)
        first.endEarly()
        killProcess(first)

        val restored = workoutViewModel()
        val state = restored.state.value
        assertTrue(state.isDone && state.ended)
        assertEquals(1, state.completedRounds)
        assertEquals("0:06", state.elapsed)
        assertEquals(first.state.value.week, state.week)
        assertEquals(1, repository.history.value.size)
    }

    @Test
    fun `a workout started afresh ignores the progress an older one left behind`() = runTest(dispatcher) {
        val first = workoutViewModel()
        run(6_000)
        killProcess(first)

        now += 60_000
        val fresh = workoutViewModel(id = WORKOUT_ID + 1)
        val state = fresh.state.value
        assertEquals(Phase.Work, state.phase)
        assertEquals(1, state.round)
        assertEquals("0:05", state.clock)
        assertEquals("0:00", state.elapsed)
        fresh.pause()
    }

    @Test
    fun `leaving the screen clears the saved progress and the notification`() = runTest(dispatcher) {
        val viewModel = workoutViewModel()
        run(2_000)
        assertEquals(WORKOUT_ID, repository.workoutProgress.value?.id)
        assertTrue(background.kept)

        leave(viewModel)
        assertNull(repository.workoutProgress.value)
        assertFalse(background.kept)
    }

    @Test
    fun `the notification follows the workout and hears of each change once`() = runTest(dispatcher) {
        val viewModel = workoutViewModel()
        assertEquals(1, background.starts)
        assertEquals(
            listOf(WorkoutStatus(Phase.Work, paused = false, clock = "0:05", round = 1, rounds = 2)),
            background.statuses,
        )

        run(1_000)
        assertEquals(listOf("0:05", "0:04"), background.statuses.map { it.clock })

        viewModel.pause()
        assertTrue(background.statuses.last().paused)
        viewModel.resume()
        assertFalse(background.statuses.last().paused)

        run(15_000)
        assertTrue(viewModel.state.value.isDone)
        assertFalse(background.kept)
        assertEquals(1, background.stops)

        viewModel.restart()
        assertEquals(2, background.starts)
        assertTrue(background.kept)
        viewModel.pause()
    }

    @Test
    fun `the notification's buttons pause and resume the workout`() = runTest(dispatcher) {
        val viewModel = workoutViewModel()
        val controls = assertNotNull(background.controls)
        controls.togglePause()
        assertTrue(viewModel.state.value.paused)
        controls.togglePause()
        assertTrue(viewModel.state.value.running)
        viewModel.pause()
    }

    @Test
    fun `End from the notification with a round done records and shows the summary`() = runTest(dispatcher) {
        val viewModel = workoutViewModel()
        run(6_000)
        assertNotNull(background.controls).end()

        val state = viewModel.state.value
        assertTrue(state.isDone && state.ended)
        assertFalse(state.leaving)
        assertEquals(1, repository.history.value.size)
        assertFalse(background.kept)
    }

    @Test
    fun `End from the notification with no round done leaves with nothing kept`() = runTest(dispatcher) {
        val viewModel = workoutViewModel()
        run(2_000)
        assertNotNull(background.controls).end()

        val state = viewModel.state.value
        assertTrue(state.leaving)
        assertFalse(state.running)
        assertTrue(repository.history.value.isEmpty())
        assertFalse(background.kept)
    }
}

private const val WORKOUT_ID = 1_700_000_000_000L
