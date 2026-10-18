@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.home

import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.estimateCalories
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimerViewModelTest : MainDispatcherTest() {
    private val repository = FakeStorage().repository(now = { 0L })
    private val viewModel by lazy { TimerViewModel(repository) }

    @Test
    fun `stepping a field updates the config and the total`() {
        viewModel.step(ConfigField.Rounds, 1)
        val state = viewModel.state.value
        assertEquals(9, state.config.rounds)
        assertEquals(formatClock(IntervalConfig().copy(rounds = 9).totalSeconds), state.total)
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

    @Test
    fun `saving a preset keeps its shape on the Timer`() {
        repository.updateConfig { it.copy(prepSeconds = 15) }
        val reshaped = IntervalConfig(prepSeconds = 0, workSeconds = 50, restSeconds = 10, rounds = 4)
        viewModel.savePreset(Preset("Mine", reshaped))
        assertEquals(reshaped.copy(prepSeconds = 15), repository.config.value)
        assertEquals(listOf("Mine"), repository.customPresets.value.map { it.name })
        assertEquals("Mine", viewModel.state.value.presetName)
    }

    @Test
    fun `the timer estimates calories only once a weight is set`() {
        assertNull(viewModel.state.value.calories)
        repository.updateAppSettings { it.copy(body = BodySettings(weightKg = 70.0, effort = Effort.Hard)) }
        val config = IntervalConfig()
        val work = config.rounds * config.workSeconds
        val expected = estimateCalories(work, config.totalSeconds - work, 70.0, Effort.Hard)
        assertEquals(expected, viewModel.state.value.calories)

        viewModel.step(ConfigField.Rounds, 1)
        assertTrue(viewModel.state.value.calories!! > expected)
    }
}
