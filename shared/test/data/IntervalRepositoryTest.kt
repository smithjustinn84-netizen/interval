package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeKeyValueStore
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntervalRepositoryTest {
    private val store = FakeKeyValueStore()
    private var clock = 1_000L
    private fun repository() = IntervalRepository(store, now = { clock })

    @Test
    fun `settings survive a restart`() {
        val config = IntervalConfig(prepSeconds = 5, workSeconds = 45, restSeconds = 15, rounds = 12)
        repository().apply {
            setConfig(config)
            setCueSettings(CueSettings(voice = false))
            saveCurrentAsPreset("Long")
        }
        val reloaded = repository()
        assertEquals(config, reloaded.config.value)
        assertEquals(CueSettings(voice = false), reloaded.cueSettings.value)
        assertEquals("Long", reloaded.customPresets.value.single().name)
    }

    @Test
    fun `an unreadable value falls back to the default`() {
        store.values["config"] = "{not json"
        assertEquals(IntervalConfig(), repository().config.value)
    }

    @Test
    fun `a blank preset name is numbered`() {
        val repository = repository()
        repository.saveCurrentAsPreset("  ")
        repository.saveCurrentAsPreset("")
        assertEquals(listOf("Custom 1", "Custom 2"), repository.customPresets.value.map { it.name })
    }

    @Test
    fun `history is newest first and capped`() {
        val repository = repository()
        repeat(IntervalRepository.MAX_HISTORY + 5) {
            clock += 1
            repository.recordWorkout(IntervalConfig())
        }
        val history = repository.history.value
        assertEquals(IntervalRepository.MAX_HISTORY, history.size)
        assertEquals(clock, history.first().finishedAtEpochMillis)
        repository.clearHistory()
        assertEquals(emptyList(), repository().history.value)
    }

    @Test
    fun `history saved before times were recorded for every workout still reads right`() {
        // The old format: a time only when ended early, and no endedEarly field.
        store.values["history"] = """[
            {"finishedAtEpochMillis":2,"config":{"workSeconds":20},"elapsedSeconds":90},
            {"finishedAtEpochMillis":1,"config":{"workSeconds":20}}
        ]"""
        val (ended, full) = repository().history.value
        assertTrue(ended.endedEarly)
        assertEquals(90, ended.durationSeconds)
        assertFalse(full.endedEarly)
        assertEquals(full.config.totalSeconds, full.durationSeconds)
    }

    @Test
    fun `a finished workout keeps its time and is not read back as ended early`() {
        repository().recordWorkout(IntervalConfig(), elapsedSeconds = 42)
        val entry = repository().history.value.single()
        assertEquals(42, entry.durationSeconds)
        assertFalse(entry.endedEarly)
    }

    @Test
    fun `applying a preset keeps the user's get-ready countdown`() {
        val repository = repository()
        repository.setConfig(IntervalConfig(prepSeconds = 30))
        repository.applyPreset(builtInPresets.first())
        assertEquals(builtInPresets.first().config.copy(prepSeconds = 30), repository.config.value)
    }

    @Test
    fun `app settings survive a restart`() {
        repository().setAppSettings(AppSettings(theme = ThemeMode.Light, keepScreenOn = false))
        assertEquals(AppSettings(theme = ThemeMode.Light, keepScreenOn = false), repository().appSettings.value)
    }
}
