package dev.justinsmith.bells.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.bells.domain.BuiltInExercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The repository over a real Room database, where a fake can't say what SQLite would refuse. */
class BellsRoomTest {
    private val database = Room.inMemoryDatabaseBuilder<BellsDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = BellsRepository(
        database.sessionDao(),
        database.bellDao(),
        database.followingDao(),
        database.customProgramDao(),
        database.customExerciseDao(),
        database.bandDao(),
        scope,
    ) { 1_000L }

    @AfterTest
    fun close() {
        scope.cancel()
        database.close()
    }

    private fun <T> await(read: () -> T, until: (T) -> Boolean): T = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            repository.active.first { until(read()) }
            read()
        }
    }

    @Test
    fun `a set removed and put back keeps its place and its id`() {
        repository.startSession()
        repository.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, null)
        repository.logSet(BuiltInExercise.Press, 16.0, 5, null)
        val sets = await({ repository.active.value?.sets.orEmpty() }) { it.size == 2 }
        val first = sets.first()
        repository.removeSet(first.id)
        await({ repository.active.value?.sets.orEmpty() }) { it.size == 1 }
        repository.restoreSet(first)
        val back = await({ repository.active.value?.sets.orEmpty() }) { it.size == 2 }
        assertEquals(sets, back)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
