@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.home

import dev.justinsmith.interval.data.FakeKeyValueStore
import dev.justinsmith.interval.data.IntervalRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimerViewModelTest {
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { 0L })
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
    fun `applying a preset names and selects it but keeps the get-ready countdown`() {
        viewModel.set(ConfigField.Prep, 25)
        val tabata = builtInPresets.first()
        viewModel.applyPreset(tabata)
        val state = viewModel.state.value
        assertEquals("Tabata", state.presetName)
        assertEquals(tabata, state.presets.single { it.selected }.preset)
        assertEquals(25, state.config.prepSeconds)
        assertEquals("0:25 to get ready", state.prepLine)
        assertFalse(state.canSavePreset)
    }

    @Test
    fun `a custom shape can be saved once`() {
        viewModel.applyPreset(builtInPresets.first())
        viewModel.step(ConfigField.Work, 1)
        assertNull(viewModel.state.value.presetName)
        assertTrue(viewModel.state.value.canSavePreset)
        viewModel.savePreset("Mine")
        assertEquals("Mine", viewModel.state.value.presetName)
        assertFalse(viewModel.state.value.canSavePreset)
    }

    @Test
    fun `no countdown means no get-ready line`() {
        viewModel.set(ConfigField.Prep, 0)
        assertNull(viewModel.state.value.prepLine)
    }
}
