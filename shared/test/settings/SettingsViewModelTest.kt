@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.settings

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.fakes.FakeKeyValueStore
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

class SettingsViewModelTest {
    private val store = FakeKeyValueStore()
    private val repository = IntervalRepository(store, now = { 0L })
    private val viewModel by lazy { SettingsViewModel(repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `toggles flip a single cue setting`() {
        viewModel.toggle(CueToggle.Halfway)
        val cues = viewModel.state.value.cues
        assertFalse(cues.halfway)
        assertTrue(cues.sound && cues.voice && cues.vibrate)
    }

    @Test
    fun `the get-ready countdown steps and clamps`() {
        viewModel.stepPrep(1)
        assertEquals(15, viewModel.state.value.prepSeconds)
        viewModel.setPrep(-5)
        assertEquals(0, viewModel.state.value.prepSeconds)
    }

    @Test
    fun `theme and keep screen on survive a restart`() {
        viewModel.setTheme(ThemeMode.Dark)
        viewModel.toggleKeepScreenOn()
        val reloaded = IntervalRepository(store, now = { 0L }).appSettings.value
        assertEquals(ThemeMode.Dark, reloaded.theme)
        assertFalse(reloaded.keepScreenOn)
    }
}
