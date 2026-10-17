@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.home

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimerViewModelTest {
    private val repository = FakeStorage().repository(now = { 0L })
    private val viewModel by lazy { TimerViewModel(repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `stepping a field updates the config and the total`() {
        viewModel.step(ConfigField.Rounds, 1)
        val state = viewModel.state.value
        assertEquals(9, state.config.rounds)
        assertEquals("6:40", state.total)
    }

    @Test
    fun `a quick pick sets the value within its range`() {
        viewModel.set(ConfigField.Work, 45)
        assertEquals(45, viewModel.state.value.config.workSeconds)
        viewModel.set(ConfigField.Rounds, 500)
        assertEquals(ConfigField.Rounds.range.last, viewModel.state.value.config.rounds)
    }

    @Test
    fun `a preset put on the Timer names the shape but keeps the get-ready countdown`() {
        viewModel.set(ConfigField.Prep, 25)
        repository.applyPreset(builtInPresets.first())
        val state = viewModel.state.value
        assertEquals("Tabata", state.presetName)
        assertEquals(25, state.config.prepSeconds)
    }

    @Test
    fun `a shape no preset has is nameless until one is saved`() {
        repository.applyPreset(builtInPresets.first())
        viewModel.step(ConfigField.Work, 1)
        assertNull(viewModel.state.value.presetName)
        repository.saveCurrentAsPreset("Mine")
        assertEquals("Mine", viewModel.state.value.presetName)
    }
}
