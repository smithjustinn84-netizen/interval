@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.setup

import dev.justinsmith.interval.data.FakeKeyValueStore
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.ConfigField
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

class SetupViewModelTest {
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { 0L })
    private val viewModel by lazy { SetupViewModel(repository, TimestampFormatter { "at $it" }) }

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
    fun `applying a preset selects it and disables saving a duplicate`() {
        val tabata = builtInPresets.first()
        viewModel.applyPreset(tabata)
        val state = viewModel.state.value
        assertTrue(state.presets.single { it.selected }.preset == tabata)
        assertFalse(state.canSavePreset)
    }

    @Test
    fun `saved presets are listed after the built-ins and can be deleted`() {
        viewModel.step(ConfigField.Work, 1)
        assertTrue(viewModel.state.value.canSavePreset)
        viewModel.savePreset("Mine")
        val custom = viewModel.state.value.presets.last()
        assertEquals("Mine", custom.preset.name)
        assertTrue(custom.custom && custom.selected)
        viewModel.deletePreset(custom.preset)
        assertEquals(builtInPresets.size, viewModel.state.value.presets.size)
    }

    @Test
    fun `history rows name the matching preset`() {
        repository.recordWorkout(builtInPresets.first().config)
        repository.recordWorkout(IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 5, rounds = 4))
        val history = viewModel.state.value.history
        assertEquals("25s on · 5s off · ×4", history[0].title)
        assertEquals("Tabata · 20s on · 10s off · ×8", history[1].title)
        assertEquals("at 0", history[1].finishedAt)
        assertEquals("4:00", history[1].total)
    }

    @Test
    fun `toggles flip a single cue setting`() {
        viewModel.toggle(CueToggle.Halfway)
        val cues = viewModel.state.value.cues
        assertFalse(cues.halfway)
        assertTrue(cues.sound && cues.voice && cues.vibrate)
    }
}
