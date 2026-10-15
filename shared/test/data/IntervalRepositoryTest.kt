package dev.justinsmith.interval.data

import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** An in-memory [KeyValueStore] for tests. */
class FakeKeyValueStore(val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

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
}
