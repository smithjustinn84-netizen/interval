@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeKeyValueStore
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { 7L })
    private val player = RecordingCuePlayer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.setConfig(IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a workout runs to done cues each phase and is recorded`() = runTest(dispatcher) {
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        assertEquals(Phase.Work, viewModel.state.value.phase)

        advanceTimeBy(15_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.isDone)
        assertFalse(state.running)
        assertEquals("2 rounds · 0:15 total", state.summary)
        assertEquals(
            listOf("Work", "3", "2", "1", "Rest", "3", "2", "1", "Last round. Work", "3", "2", "1", "Done. Nice work"),
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
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        advanceTimeBy(2_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        viewModel.pause()
        advanceTimeBy(10_000)
        viewModel.resume()
        // Skipping the rest of the work interval adds nothing.
        viewModel.skip()

        viewModel.endEarly()
        assertEquals("0:02", viewModel.state.value.elapsed)
        assertEquals("1 of 2 rounds · 0:02", viewModel.state.value.summary)
        val entry = repository.history.value.single()
        assertEquals(2, entry.elapsedSeconds)
        assertTrue(entry.endedEarly)
    }

    @Test
    fun `back returns to the interval before and add time extends this one without cues`() = runTest(dispatcher) {
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
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
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        advanceTimeBy(2_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        viewModel.pause()
        val paused = viewModel.state.value
        assertTrue(paused.paused)
        assertEquals("Paused", paused.label)

        advanceTimeBy(10_000)
        assertEquals(paused.clock, viewModel.state.value.clock)

        viewModel.resume()
        assertTrue(viewModel.state.value.running)
        viewModel.pause()
    }

    @Test
    fun `cues respect the user's toggles`() = runTest(dispatcher) {
        repository.setCueSettings(CueSettings(sound = false, voice = false, vibrate = false))
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        viewModel.skip()
        viewModel.skip()
        viewModel.skip()
        assertTrue(viewModel.state.value.isDone)
        assertTrue(player.beeps.isEmpty() && player.spoken.isEmpty() && player.haptics.isEmpty())
    }

    @Test
    fun `restart begins again from the first interval`() = runTest(dispatcher) {
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        viewModel.skip()
        viewModel.skip()
        viewModel.restart()
        val state = viewModel.state.value
        assertEquals(Phase.Work, state.phase)
        assertEquals("Round 1 of 2 · 0:15 left", state.meta)
        viewModel.pause()
    }

    @Test
    fun `ending early records the time spent and shows the summary`() = runTest(dispatcher) {
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        advanceTimeBy(6_000 + WorkoutViewModel.TICK_MILLIS)
        runCurrent()
        assertTrue(viewModel.state.value.hasProgress)

        viewModel.endEarly()
        val state = viewModel.state.value
        assertTrue(state.isDone && state.ended)
        assertFalse(state.running)
        assertEquals("1 of 2 rounds · 0:06", state.summary)
        assertEquals("0:06", state.elapsed)
        assertEquals(6, repository.history.value.single().elapsedSeconds)
        assertTrue(repository.history.value.single().endedEarly)

        viewModel.resume()
        assertFalse(viewModel.state.value.running)
    }

    @Test
    fun `a summary offers saving only a shape no preset has`() = runTest(dispatcher) {
        val viewModel = WorkoutViewModel(repository, player, testScheduler.timeSource)
        repeat(3) { viewModel.skip() }
        assertEquals("0:00", viewModel.state.value.elapsed)
        assertEquals(0, repository.history.value.single().elapsedSeconds)
        assertTrue(viewModel.state.value.canSavePreset)
        assertEquals("Custom · 5s on · 5s off · ×2", viewModel.state.value.shape)
        viewModel.saveAsPreset("Short")
        assertFalse(viewModel.state.value.canSavePreset)
        assertEquals("Short · 5s on · 5s off · ×2", viewModel.state.value.shape)
    }
}
