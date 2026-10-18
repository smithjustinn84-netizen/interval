@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.estimateCalories
import dev.justinsmith.interval.timer.stepped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntervalRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `settings survive a restart`() {
        val config = IntervalConfig(prepSeconds = 5, workSeconds = 45, restSeconds = 15, rounds = 12)
        repository().apply {
            updateConfig { config }
            updateCueSettings { it.copy(voice = false) }
            saveCurrentAsPreset("Long")
        }
        val reloaded = repository()
        assertEquals(config, reloaded.config.value)
        assertEquals(CueSettings(voice = false), reloaded.cueSettings.value)
        assertEquals("Long", reloaded.customPresets.value.single().name)
    }

    @Test
    fun `app settings survive a restart`() {
        repository().updateAppSettings { AppSettings(theme = ThemeMode.Light, keepScreenOn = false) }
        assertEquals(AppSettings(theme = ThemeMode.Light, keepScreenOn = false), repository().appSettings.value)
    }

    @Test
    fun `values hold defaults and ready stays false until settings have been read`() = runTest {
        storage.repository().updateConfig { IntervalConfig(workSeconds = 99) }
        val stored = storage.settings.data.first()
        val slow = SlowDataStore()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val repository =
            IntervalRepository(slow, storage.history, storage.presets, storage.planned, scope, clock = { clock })
        assertFalse(repository.ready.value)
        assertEquals(IntervalConfig(), repository.config.value)

        slow.data.emit(stored)
        assertTrue(repository.ready.value)
        assertEquals(99, repository.config.value.workSeconds)
    }

    @Test
    fun `ready turns true once settings presets and history are read`() {
        assertTrue(repository().ready.value)
    }

    @Test
    fun `a read that fails still lets the app start with defaults`() {
        val broken = object : HistoryDao by storage.history {
            override fun observeAll(): Flow<List<HistoryEntity>> = flow { error("disk on fire") }
        }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val repository =
            IntervalRepository(storage.settings, broken, storage.presets, storage.planned, scope, clock = { clock })
        assertTrue(repository.ready.value)
        assertEquals(emptyList(), repository.history.value)
    }

    @Test
    fun `a list whose read fails once follows the database again`() = runTest {
        storage.repository().recordWorkout(IntervalConfig(), elapsedSeconds = 5)
        var reads = 0
        val flaky = object : HistoryDao by storage.history {
            // Room's flow queries again each time it is collected; this one fails the first time.
            override fun observeAll(): Flow<List<HistoryEntity>> = flow {
                if (reads++ == 0) error("disk hiccup")
                emitAll(storage.history.observeAll())
            }
        }
        val scope = deferredStorageScope()
        val repository =
            IntervalRepository(storage.settings, flaky, storage.presets, storage.planned, scope, clock = { clock })
        testScheduler.runCurrent()
        assertTrue(repository.ready.value)
        assertEquals(emptyList(), repository.history.value)

        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.history.value.size)
    }

    @Test
    fun `updates apply to the current value so quick steps all land`() {
        val repository = repository()
        repeat(3) { repository.updateConfig { it.stepped(ConfigField.Rounds, +1) } }
        assertEquals(IntervalConfig().rounds + 3, repository.config.value.rounds)
        assertEquals(IntervalConfig().rounds + 3, repository().config.value.rounds)
    }

    @Test
    fun `a blank preset name is the shape's own and never repeats`() {
        val repository = repository()
        repository.updateConfig { IntervalConfig(workSeconds = 40, restSeconds = 20, rounds = 6) }
        repository.saveCurrentAsPreset("  ")
        repository.saveCurrentAsPreset("")
        assertEquals(listOf("40/20 ×6", "40/20 ×6 2"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `a name another preset has is numbered`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Tabata")
        repository.saveCurrentAsPreset("Mine")
        repository.saveCurrentAsPreset("  Mine ")
        assertEquals(listOf("Tabata 2", "Mine", "Mine 2"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `a saved preset keeps the shape it was given`() {
        val repository = repository()
        val shape = IntervalConfig(prepSeconds = 10, workSeconds = 40, restSeconds = 20, rounds = 6)
        repository.savePreset(Preset("Legs", shape))
        val saved = repository.customPresets.value.single()
        assertEquals(shape, saved.config)
        assertEquals(shape, repository().customPresets.value.single().config)
        assertEquals(saved, repository.presets.value.single { it.custom })
    }

    @Test
    fun `updating a preset rewrites its name and shape in place`() {
        val repository = repository()
        repository.saveCurrentAsPreset("First")
        repository.saveCurrentAsPreset("Second")
        val first = repository.customPresets.value.first()
        repository.updatePreset(first.copy(name = "Renamed", config = first.config.copy(rounds = 3)))
        val (updated, second) = repository.customPresets.value
        assertEquals(first.id, updated.id)
        assertEquals("Renamed", updated.name)
        assertEquals(3, updated.config.rounds)
        assertEquals("Second", second.name)
        assertEquals(listOf("Renamed", "Second"), repository().customPresets.value.map { it.name })
    }

    @Test
    fun `updating keeps a preset's own name but not another's`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.saveCurrentAsPreset("Other")
        val (mine, other) = repository.customPresets.value
        repository.updatePreset(mine.copy(config = mine.config.copy(rounds = 2)))
        repository.updatePreset(other.copy(name = "Mine"))
        assertEquals(listOf("Mine", "Mine 2"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `updating a built-in or an unknown id changes nothing`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.updatePreset(builtInPresets.first().copy(name = "Renamed"))
        repository.updatePreset(Preset("Ghost", IntervalConfig(), id = 99))
        assertEquals(listOf("Mine"), repository.customPresets.value.map { it.name })
        assertEquals(builtInPresets, repository.presets.value.filterNot { it.custom })
    }

    @Test
    fun `deleting a preset removes only that one even beside a twin`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Twin")
        repository.saveCurrentAsPreset("Twin")
        val (first, second) = repository.customPresets.value
        repository.deletePreset(first)
        assertEquals(listOf(second), repository.customPresets.value)
    }

    @Test
    fun `presets list the built-ins then the saved ones marked custom`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val presets = repository.presets.value
        assertEquals(builtInPresets + repository.customPresets.value, presets)
        assertEquals(listOf("Mine"), presets.filter { it.custom }.map { it.name })
    }

    @Test
    fun `deleting a built-in preset does nothing`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        repository.deletePreset(builtInPresets.first())
        assertEquals(listOf("Mine"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `history is newest first keeps every workout and clears`() {
        val repository = repository()
        repeat(50) {
            clock += 1
            repository.recordWorkout(IntervalConfig(), elapsedSeconds = it)
        }
        val history = repository.history.value
        assertEquals(50, history.size)
        assertEquals(clock, history.first().finishedAtEpochMillis)
        repository.clearHistory()
        assertEquals(emptyList(), repository().history.value)
    }

    @Test
    fun `a workout keeps its time and whether it ended early`() {
        repository().recordWorkout(IntervalConfig(), elapsedSeconds = 42)
        repository().recordWorkout(IntervalConfig(), elapsedSeconds = 7, endedEarly = true)
        val (early, full) = repository().history.value
        assertEquals(42, full.elapsedSeconds)
        assertFalse(full.endedEarly)
        assertEquals(7, early.elapsedSeconds)
        assertTrue(early.endedEarly)
    }

    @Test
    fun `a workout keeps its work time and older ones have none`() {
        repository().recordWorkout(IntervalConfig(), elapsedSeconds = 300, workSeconds = 240)
        repository().recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        val (older, newer) = repository().history.value
        assertEquals(240, newer.workActiveSeconds)
        assertEquals(null, older.workActiveSeconds)
    }

    @Test
    fun `body settings survive a restart`() {
        val body = BodySettings(weightKg = 72.5, unit = WeightUnit.Lb, effort = Effort.Max)
        repository().updateAppSettings { it.copy(body = body) }
        assertEquals(body, repository().appSettings.value.body)
        repository().updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        assertEquals(null, repository().appSettings.value.body.weightKg)
    }

    @Test
    fun `a history entry has calories only with a weight`() {
        val config = IntervalConfig(prepSeconds = 0, workSeconds = 60, restSeconds = 60, rounds = 2)
        val entry = HistoryEntry(0, config, elapsedSeconds = 180, endedEarly = false, workActiveSeconds = 120)
        assertEquals(null, entry.calories(BodySettings()))
        val body = BodySettings(weightKg = 80.0, effort = Effort.Hard)
        assertEquals(estimateCalories(120, 60, 80.0, Effort.Hard), entry.calories(body))
        // Recorded before work time was kept: the plan's work time stands in.
        assertEquals(entry.calories(body), entry.copy(workActiveSeconds = null).calories(body))
    }

    @Test
    fun `applying a preset keeps the Timer's activity unless the preset names one`() {
        val repository = repository()
        repository.updateConfig { it.copy(activity = Activity.Bike) }
        repository.applyPreset(builtInPresets.first { it.name == "Tabata" })
        assertEquals(Activity.Bike, repository.config.value.activity)
        repository.applyPreset(builtInPresets.first { it.name == "Hangboard" })
        assertEquals(Activity.Climbing, repository.config.value.activity)
    }

    @Test
    fun `presets and history keep their activity`() {
        val config = IntervalConfig(activity = Activity.JumpRope)
        repository().updateConfig { config }
        repository().saveCurrentAsPreset("Rope")
        repository().recordWorkout(config, elapsedSeconds = 60)
        assertEquals(Activity.JumpRope, repository().customPresets.value.single().config.activity)
        assertEquals(Activity.JumpRope, repository().history.value.single().config.activity)
        assertEquals(Activity.JumpRope, repository().config.value.activity)
    }

    @Test
    fun `applying a preset keeps the user's get-ready countdown`() {
        val repository = repository()
        repository.updateConfig { IntervalConfig(prepSeconds = 30) }
        repository.applyPreset(builtInPresets.first())
        assertEquals(builtInPresets.first().config.copy(prepSeconds = 30), repository.config.value)
    }

    @Test
    fun `two saves made before either lands still get distinct names`() = runTest {
        val repository = storage.repository(now = { clock }, scope = deferredStorageScope())
        testScheduler.runCurrent()
        repository.savePreset(Preset("Twin", IntervalConfig()))
        repository.savePreset(Preset("Twin", IntervalConfig()))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Twin", "Twin 2"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `a write that fails is dropped and the next one still lands`() {
        var inserts = 0
        val flaky = object : HistoryDao by storage.history {
            override suspend fun insert(entry: HistoryEntity) {
                if (inserts++ == 0) error("disk full")
                storage.history.insert(entry)
            }
        }
        val repository =
            IntervalRepository(
                storage.settings,
                flaky,
                storage.presets,
                storage.planned,
                CoroutineScope(
                    Dispatchers.Unconfined,
                ),
                clock = {
                    clock
                },
            )
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        assertEquals(listOf(2), repository.history.value.map { it.elapsedSeconds })
    }

    @Test
    fun `one workout can be deleted by its id`() {
        val repository = repository()
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        clock += 1
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        val (newer, older) = repository.history.value
        repository.deleteWorkout(requireNotNull(older.id))
        assertEquals(listOf(newer), repository().history.value)
    }

    @Test
    fun `a weekly plan is one plan per weekday and survives a restart`() {
        val tabata = builtInPresets.first()
        repository().planWeekly(tabata, setOf(4, 1, 9))
        val planned = repository().planned.value
        assertEquals(listOf(PlanSchedule.Weekly(1), PlanSchedule.Weekly(4)), planned.map { it.schedule })
        assertTrue(planned.all { it.name == tabata.name && it.config == tabata.config && it.presetId == null })
        assertTrue(planned.all { it.createdAtEpochMillis == clock })
    }

    @Test
    fun `a one-off plan keeps its day and the user's preset it came from`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val mine = repository.customPresets.value.single()
        repository.planOnce(mine, epochDay = 20_744)
        val planned = repository().planned.value.single()
        assertEquals(PlanSchedule.Once(20_744), planned.schedule)
        assertEquals(mine.id, planned.presetId)
        assertTrue(planned.fallsOn(20_744))
        assertFalse(planned.fallsOn(20_745))
    }

    @Test
    fun `a plan stays as made when its preset is deleted and can be removed`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val mine = repository.customPresets.value.single()
        repository.planWeekly(mine, setOf(2))
        repository.deletePreset(mine)
        val planned = repository.planned.value.single()
        assertEquals("Mine", planned.name)
        repository.removePlanned(planned.id)
        assertEquals(emptyList(), repository().planned.value)
    }

    @Test
    fun `the weekly goal survives a restart and clears`() {
        repository().updateAppSettings { it.copy(weeklyGoal = 4) }
        assertEquals(4, repository().appSettings.value.weeklyGoal)
        repository().updateAppSettings { it.copy(weeklyGoal = null) }
        assertEquals(null, repository().appSettings.value.weeklyGoal)
    }
}

/** Settings whose first read hasn't come back yet: [data] emits only when the test says so. */
private class SlowDataStore : DataStore<Preferences> {
    override val data = MutableSharedFlow<Preferences>()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        error("Not used")
}
