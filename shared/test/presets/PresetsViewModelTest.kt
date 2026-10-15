@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.presets

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeKeyValueStore
import dev.justinsmith.interval.timer.IntervalConfig
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
import kotlin.test.assertTrue

class PresetsViewModelTest {
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { 0L })
    private val viewModel by lazy { PresetsViewModel(repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `built-ins and the user's own are listed apart`() {
        repository.setConfig(IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 10, rounds = 8))
        viewModel.saveCurrent("Morning")
        val state = viewModel.state.value
        assertEquals(builtInPresets, state.builtIn.map { it.preset })
        assertEquals(listOf("Morning"), state.custom.map { it.preset.name })
        assertTrue(state.custom.single().selected)
        assertFalse(state.canSaveCurrent)
    }

    @Test
    fun `totals include the user's get-ready countdown`() {
        repository.setConfig(IntervalConfig(prepSeconds = 0))
        // Tabata without its 10s countdown: 8 × 20s work and 7 × 10s rest.
        assertEquals("3:50", viewModel.state.value.builtIn.first().total)
    }

    @Test
    fun `applying selects a preset and deleting removes it`() {
        viewModel.apply(builtInPresets[1])
        assertEquals(builtInPresets[1], viewModel.state.value.builtIn.single { it.selected }.preset)
        repository.setConfig(IntervalConfig(workSeconds = 33))
        viewModel.saveCurrent("Odd")
        viewModel.delete(viewModel.state.value.custom.single().preset)
        assertTrue(viewModel.state.value.custom.isEmpty())
        assertTrue(viewModel.state.value.canSaveCurrent)
    }
}
