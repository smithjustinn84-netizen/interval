@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.settings

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.fromKg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val storage = FakeStorage()
    private val repository = storage.repository()
    private val player = RecordingCuePlayer()
    private val viewModel by lazy { SettingsViewModel(repository, player) }

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

    @Test
    fun `the weight starts at the default and can be turned off`() {
        assertEquals(WeightUnit.Kg.default, viewModel.state.value.weight)
        viewModel.setWeight(82)
        assertEquals(82, viewModel.state.value.weight)
        viewModel.clearWeight()
        assertNull(viewModel.state.value.weight)
    }

    @Test
    fun `a weight in pounds is kept in kilograms and shown in pounds`() {
        viewModel.setWeightUnit(WeightUnit.Lb)
        viewModel.setWeight(180)
        assertEquals(180, viewModel.state.value.weight)
        assertEquals(81.6, repository.appSettings.value.body.weightKg!!, absoluteTolerance = 0.1)
        viewModel.setWeightUnit(WeightUnit.Kg)
        assertEquals(82, viewModel.state.value.weight)
    }

    @Test
    fun `a weight is clamped to its unit's range`() {
        viewModel.setWeight(5)
        assertEquals(WeightUnit.Kg.range.first, viewModel.state.value.weight)
    }

    @Test
    fun `weight unit and effort survive a restart`() {
        viewModel.setWeightUnit(WeightUnit.Lb)
        viewModel.setWeight(150)
        viewModel.setEffort(Effort.Easy)
        val reloaded = storage.repository().appSettings.value.body
        assertEquals(WeightUnit.Lb, reloaded.unit)
        assertEquals(Effort.Easy, reloaded.effort)
        assertEquals(150, WeightUnit.Lb.fromKg(reloaded.weightKg!!))
    }
}
