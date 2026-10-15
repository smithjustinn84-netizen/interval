@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.FakeKeyValueStore
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
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

private class RecordingPlayer : CuePlayer {
    val beeps = mutableListOf<Beep>()
    val spoken = mutableListOf<String>()
    val haptics = mutableListOf<Haptic>()

    override fun beep(beep: Beep) {
        beeps += beep
    }

    override fun speak(text: String) {
        spoken += text
    }

    override fun vibrate(haptic: Haptic) {
        haptics += haptic
    }

    override fun release() = Unit
}

class WorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { 7L })
    private val player = RecordingPlayer()

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
        assertEquals(7L, repository.history.value.single().finishedAtEpochMillis)
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
}
