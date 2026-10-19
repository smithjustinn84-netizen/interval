@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.presets

import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetsViewModelTest : MainDispatcherTest() {
    private val repository = FakeStorage().repository(now = { 0L })
    private val viewModel by lazy { PresetsViewModel(repository) }

    @Test
    fun `the user's own come first and apart from the built-ins`() {
        val morning = IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 10, rounds = 8)
        repository.updateConfig { morning }
        viewModel.save(Preset("Morning", morning))
        val state = viewModel.state.value
        assertEquals(builtInPresets, state.builtIn.map { it.preset })
        assertEquals(listOf("Morning"), state.custom.map { it.preset.name })
        assertEquals(listOf("Morning") + builtInPresets.map { it.name }, state.rows.map { it.preset.name })
        assertTrue(state.custom.single().selected)
        assertTrue(state.custom.single().preset.custom)
        assertFalse(state.canSaveCurrent)
    }

    @Test
    fun `totals include the user's get-ready countdown`() {
        repository.updateConfig { IntervalConfig(prepSeconds = 0) }
        // Tabata without its 10s countdown: 8 × 20s work and 7 × 10s rest.
        assertEquals("3:50", viewModel.state.value.builtIn.first().total)
    }

    @Test
    fun `applying selects a preset and deleting removes it`() {
        viewModel.putOnTimer(builtInPresets[1])
        assertEquals(builtInPresets[1], viewModel.state.value.builtIn.single { it.selected }.preset)
        repository.updateConfig { IntervalConfig(workSeconds = 33) }
        viewModel.save(Preset("Odd", repository.config.value))
        viewModel.delete(viewModel.state.value.custom.single().preset)
        assertTrue(viewModel.state.value.custom.isEmpty())
        assertTrue(viewModel.state.value.canSaveCurrent)
    }

    @Test
    fun `saving a preset with an id rewrites that one`() {
        viewModel.save(Preset("Mine", IntervalConfig(workSeconds = 33)))
        val saved = viewModel.state.value.custom.single().preset
        viewModel.save(saved.copy(name = "Renamed", config = saved.config.copy(rounds = 3)))
        val updated = viewModel.state.value.custom.single().preset
        assertEquals(saved.id, updated.id)
        assertEquals("Renamed", updated.name)
        assertEquals(3, updated.config.rounds)
    }

    @Test
    fun `a blank name is the shape's own`() {
        viewModel.save(Preset("", IntervalConfig(workSeconds = 40, restSeconds = 20, rounds = 6)))
        assertEquals("40/20 ×6", viewModel.state.value.custom.single().preset.name)
    }

    @Test
    fun `timelines are scaled to the longest workout on the screen`() {
        repository.updateConfig { builtInPresets.first().config }
        val state = viewModel.state.value
        val longest = state.builtIn.maxBy { it.share }
        assertEquals("Long efforts", longest.preset.name)
        assertEquals(1f, longest.share)
        // Tabata, 4:00 with its countdown, against Long efforts' 11:55.
        assertEquals(240f / 715f, state.builtIn.first().share)
    }

    @Test
    fun `unsaved settings longer than every preset set the scale`() {
        repository.updateConfig { IntervalConfig(prepSeconds = 10, workSeconds = 60, restSeconds = 30, rounds = 12) }
        val state = viewModel.state.value
        assertTrue(state.canSaveCurrent)
        assertEquals(1f, state.currentShare)
        assertTrue(state.builtIn.all { it.share < 1f })
    }

    @Test
    fun `a preset of the user's own with a built-in's shape is the one on the Timer`() {
        viewModel.putOnTimer(builtInPresets.first())
        viewModel.save(Preset("My Tabata", builtInPresets.first().config))
        val state = viewModel.state.value
        assertEquals(listOf("My Tabata"), state.rows.filter { it.selected }.map { it.preset.name })
    }

    @Test
    fun `editing the preset on the Timer puts its new shape there`() {
        val morning = IntervalConfig(prepSeconds = 5, workSeconds = 25, restSeconds = 10, rounds = 8)
        repository.updateConfig { morning }
        viewModel.save(Preset("Morning", morning))
        val saved = viewModel.state.value.custom.single().preset
        viewModel.save(saved.copy(config = saved.config.copy(rounds = 10)))
        assertEquals(10, repository.config.value.rounds)
        assertEquals(5, repository.config.value.prepSeconds)
        assertFalse(viewModel.state.value.canSaveCurrent)
        assertTrue(viewModel.state.value.custom.single().selected)
    }

    @Test
    fun `editing a preset that is not on the Timer leaves the Timer alone`() {
        viewModel.putOnTimer(builtInPresets.first())
        viewModel.save(Preset("Odd", IntervalConfig(workSeconds = 33)))
        val saved = viewModel.state.value.custom.single().preset
        viewModel.save(saved.copy(config = saved.config.copy(rounds = 3)))
        assertEquals(builtInPresets.first().config.rounds, repository.config.value.rounds)
    }

    @Test
    fun `saving the Timer's settings with a new shape keeps that shape on the Timer`() {
        repository.updateConfig { IntervalConfig(workSeconds = 33) }
        viewModel.saveCurrent(Preset("Sprints", IntervalConfig(workSeconds = 33, rounds = 4)))
        assertEquals(4, repository.config.value.rounds)
        assertEquals("Sprints", viewModel.state.value.custom.single { it.selected }.preset.name)
    }

    @Test
    fun `each preset estimates its calories with the user's get-ready countdown`() {
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        assertTrue(viewModel.state.value.rows.all { it.calories == null })
        repository.updateConfig { it.copy(prepSeconds = 30) }
        val body = BodySettings(weightKg = 80.0, effort = Effort.Max)
        repository.updateAppSettings { it.copy(body = body) }
        val tabata = viewModel.state.value.builtIn.first()
        assertEquals(body.plannedCalories(tabata.preset.config.copy(prepSeconds = 30)), tabata.calories)
        assertEquals(body.plannedCalories(repository.config.value), viewModel.state.value.currentCalories)
    }

    @Test
    fun `a preset is estimated as it would run from the Timer`() {
        repository.updateAppSettings { it.copy(body = BodySettings(weightKg = 70.0)) }
        repository.updateConfig { it.copy(activity = Activity.Bike) }
        val rows = viewModel.state.value.builtIn.associateBy { it.preset.name }
        val tabata = rows.getValue("Tabata")
        val hangboard = rows.getValue("Hangboard")
        val body = repository.appSettings.value.body
        val prep = repository.config.value.prepSeconds
        // Tabata names no activity, so it would run as the Timer's; Hangboard names its own.
        assertEquals(
            body.plannedCalories(tabata.preset.config.copy(prepSeconds = prep, activity = Activity.Bike)),
            tabata.calories,
        )
        assertEquals(body.plannedCalories(hangboard.preset.config.copy(prepSeconds = prep)), hangboard.calories)
        // Only the borrowed activity is named beside the estimate; Hangboard's own is in its shape line.
        assertEquals(Activity.Bike, tabata.estimatedAs)
        assertEquals(null, hangboard.estimatedAs)

        repository.updateConfig { it.copy(activity = Activity.General) }
        assertEquals(null, viewModel.state.value.builtIn.first { it.preset.name == "Tabata" }.estimatedAs)
    }
}
