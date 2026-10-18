@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutViewModelTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val player = RecordingCuePlayer()
    private val viewModels = mutableListOf<WorkoutViewModel>()

    @BeforeTest
    fun setUp() {
        repository.updateConfig { IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2) }
    }

    // Stops every ticker, so none outlives its test on the shared scheduler.
    @AfterTest
    fun clearViewModels() = viewModels.forEach { it.viewModelScope.cancel() }

    /** A workout over [repository], timed by the test's clock, stopped once the test ends. */
    private fun TestScope.workoutViewModel(repository: IntervalRepository = this@WorkoutViewModelTest.repository) =
        WorkoutViewModel(repository, player, testScheduler.timeSource).also { viewModels += it }

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
}
