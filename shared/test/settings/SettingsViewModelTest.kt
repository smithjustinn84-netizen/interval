@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.settings

import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
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
    private val storage = FakeStorage()
    private val repository = storage.repository()
    private val player = RecordingCuePlayer()
    private val viewModel by lazy { SettingsViewModel(repository, player) }

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
    fun `switching a cue on plays it once`() {
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Vibrate)
        viewModel.toggle(CueToggle.Vibrate)
        assertEquals(listOf(Beep.Work), player.beeps)
        assertEquals(listOf(Haptic.Work), player.haptics)
        assertTrue(player.spoken.isEmpty())
    }

    @Test
    fun `switching voice on says a phase name`() {
        viewModel.toggle(CueToggle.Voice)
        viewModel.toggle(CueToggle.Voice)
        assertEquals(listOf<Speech>(Speech.Work(lastRound = false)), player.spoken)
    }

    @Test
    fun `the halfway sample is spoken when sound is off`() {
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Halfway)
        viewModel.toggle(CueToggle.Halfway)
        assertTrue(player.beeps.isEmpty())
        assertEquals(listOf<Speech>(Speech.Halfway), player.spoken)
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
        val reloaded = storage.repository().appSettings.value
        assertEquals(ThemeMode.Dark, reloaded.theme)
        assertFalse(reloaded.keepScreenOn)
    }
}
