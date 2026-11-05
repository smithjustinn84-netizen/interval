@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The presets through [PresetRepository]: naming, saving, updating, deleting and putting one on the Timer. */
class PresetRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `a blank preset name is the shape's own and never repeats`() {
        val repository = repository()
        repository.settings.updateConfig { IntervalConfig(workSeconds = 40, restSeconds = 20, rounds = 6) }
        repository.saveCurrentAsPreset("  ")
        repository.saveCurrentAsPreset("")
        assertEquals(listOf("40/20 ×6", "40/20 ×6 2"), repository.presets.customPresets.value.map { it.name })
    }

    @Test
    fun `a name another preset has is numbered`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Tabata")
        repository.saveCurrentAsPreset("Mine")
        repository.saveCurrentAsPreset("  Mine ")
        assertEquals(listOf("Tabata 2", "Mine", "Mine 2"), repository.presets.customPresets.value.map { it.name })
    }

    @Test
    fun `a saved preset keeps the shape it was given`() {
        val repository = repository()
        val shape = IntervalConfig(prepSeconds = 10, workSeconds = 40, restSeconds = 20, rounds = 6)
        repository.presets.savePreset(Preset("Legs", shape))
        val saved = repository.presets.customPresets.value.single()
        assertEquals(shape, saved.config)
        assertEquals(shape, repository().presets.customPresets.value.single().config)
        assertEquals(saved, repository.presets.presets.value.single { it.custom })
    }

    @Test
    fun `updating a preset rewrites its name and shape in place`() {
        val repository = repository()
        repository.saveCurrentAsPreset("First")
        repository.saveCurrentAsPreset("Second")
        val first = repository.presets.customPresets.value.first()
        repository.presets.updatePreset(first.copy(name = "Renamed", config = first.config.copy(rounds = 3)))
        val (updated, second) = repository.presets.customPresets.value
        assertEquals(first.id, updated.id)
        assertEquals("Renamed", updated.name)
        assertEquals(3, updated.config.rounds)
        assertEquals("Second", second.name)
        assertEquals(listOf("Renamed", "Second"), repository().presets.customPresets.value.map { it.name })
    }

    @Test
    fun `updating keeps a preset's own name but not another's`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.saveCurrentAsPreset("Other")
        val (mine, other) = repository.presets.customPresets.value
        repository.presets.updatePreset(mine.copy(config = mine.config.copy(rounds = 2)))
        repository.presets.updatePreset(other.copy(name = "Mine"))
        assertEquals(listOf("Mine", "Mine 2"), repository.presets.customPresets.value.map { it.name })
    }

    @Test
    fun `updating a built-in or an unknown id changes nothing`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.presets.updatePreset(builtInPresets.first().copy(name = "Renamed"))
        repository.presets.updatePreset(Preset("Ghost", IntervalConfig(), id = 99))
        assertEquals(listOf("Mine"), repository.presets.customPresets.value.map { it.name })
        assertEquals(builtInPresets, repository.presets.presets.value.filterNot { it.custom })
    }

    @Test
    fun `deleting a preset removes only that one even beside a twin`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Twin")
        repository.saveCurrentAsPreset("Twin")
        val (first, second) = repository.presets.customPresets.value
        repository.presets.deletePreset(first)
        assertEquals(listOf(second), repository.presets.customPresets.value)
    }

    @Test
    fun `a deleted preset restored keeps its id and its place in the list`() {
        val repository = repository()
        repository.saveCurrentAsPreset("First")
        repository.saveCurrentAsPreset("Second")
        repository.saveCurrentAsPreset("Third")
        val second = repository.presets.customPresets.value[1]
        repository.presets.deletePreset(second)
        assertEquals(listOf("First", "Third"), repository.presets.customPresets.value.map { it.name })
        repository.presets.restorePreset(second)
        assertEquals(listOf("First", "Second", "Third"), repository.presets.customPresets.value.map { it.name })
        assertEquals(second.id, repository.presets.customPresets.value[1].id)
    }

    @Test
    fun `presets list the built-ins then the saved ones marked custom`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val presets = repository.presets.presets.value
        assertEquals(builtInPresets + repository.presets.customPresets.value, presets)
        assertEquals(listOf("Mine"), presets.filter { it.custom }.map { it.name })
        repository.presets.deletePreset(repository.presets.customPresets.value.single())
        assertEquals(builtInPresets, repository.presets.presets.value)
        assertEquals(emptyList(), repository.presets.customPresets.value)
    }

    @Test
    fun `deleting a built-in preset does nothing`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.presets.deletePreset(builtInPresets.first())
        assertEquals(listOf("Mine"), repository.presets.customPresets.value.map { it.name })
    }

    @Test
    fun `applying a preset keeps the Timer's activity unless the preset names one`() {
        val repository = repository()
        repository.settings.updateConfig { it.copy(activity = Activity.Bike) }
        repository.presets.applyPreset(builtInPresets.first { it.name == "Tabata" })
        assertEquals(Activity.Bike, repository.settings.config.value.activity)
        repository.presets.applyPreset(builtInPresets.first { it.name == "Hangboard" })
        assertEquals(Activity.Climbing, repository.settings.config.value.activity)
    }

    @Test
    fun `applying a preset keeps the user's get-ready countdown`() {
        val repository = repository()
        repository.settings.updateConfig { IntervalConfig(prepSeconds = 30) }
        repository.presets.applyPreset(builtInPresets.first())
        assertEquals(builtInPresets.first().config.copy(prepSeconds = 30), repository.settings.config.value)
    }

    @Test
    fun `two saves made before either lands still get distinct names`() = runTest {
        val repository = storage.repository(now = { clock }, scope = deferredStorageScope())
        testScheduler.runCurrent()
        repository.presets.savePreset(Preset("Twin", IntervalConfig()))
        repository.presets.savePreset(Preset("Twin", IntervalConfig()))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Twin", "Twin 2"), repository.presets.customPresets.value.map { it.name })
    }
}
