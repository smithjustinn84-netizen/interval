@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.storage.StorageError
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What only [IntervalRepository] does over its repositories: ready, a failed read or write, the shared queue. */
class IntervalRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `values hold defaults and ready stays false until settings have been read`() = runTest {
        storage.repository().settings.updateConfig { IntervalConfig(workSeconds = 99) }
        val stored = storage.settings.data.first()
        val slow = SlowDataStore()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val repository =
            IntervalRepository(
                slow,
                storage.history,
                storage.presets,
                storage.planned,
                storage.sports,
                storage.imports,
                scope,
                clock = { clock },
            )
        assertFalse(repository.ready.value)
        assertEquals(IntervalConfig(), repository.settings.config.value)

        slow.data.emit(stored)
        assertTrue(repository.ready.value)
        assertEquals(99, repository.settings.config.value.workSeconds)
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
            IntervalRepository(
                storage.settings,
                broken,
                storage.presets,
                storage.planned,
                storage.sports,
                storage.imports,
                scope,
                clock = { clock },
            )
        assertTrue(repository.ready.value)
        assertEquals(emptyList(), repository.history.history.value)
    }

    @Test
    fun `a list whose read fails once follows the database again`() = runTest {
        storage.repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 5)
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
            IntervalRepository(
                storage.settings,
                flaky,
                storage.presets,
                storage.planned,
                storage.sports,
                storage.imports,
                scope,
                clock = { clock },
            )
        testScheduler.runCurrent()
        assertTrue(repository.ready.value)
        assertEquals(emptyList(), repository.history.history.value)

        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.history.history.value.size)
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
                storage.sports,
                storage.imports,
                CoroutineScope(
                    Dispatchers.Unconfined,
                ),
                clock = {
                    clock
                },
            )
        val errors = mutableListOf<StorageError>()
        val watching = CoroutineScope(Dispatchers.Unconfined).launch { repository.writeErrors.collect { errors += it } }
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        assertEquals(listOf(2), repository.history.history.value.map { it.elapsedSeconds })
        assertEquals(listOf("disk full"), errors.map { it.cause.message })
        watching.cancel()
    }
}

/** Settings whose first read hasn't come back yet: [data] emits only when the test says so. */
private class SlowDataStore : DataStore<Preferences> {
    override val data = MutableSharedFlow<Preferences>()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        error("Not used")
}
