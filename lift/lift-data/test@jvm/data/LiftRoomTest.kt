package dev.justinsmith.lift.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.SetLog
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
import kotlin.test.assertFailsWith

/** The repository over a real Room database, where a fake can't say what SQLite would refuse. */
class LiftRoomTest {
    private val database = Room.inMemoryDatabaseBuilder<LiftDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = LiftRepository(
        database.sessionDao(),
        database.bellDao(),
        database.followingDao(),
        database.customProgramDao(),
        database.customExerciseDao(),
        database.bandDao(),
        database.importDao(),
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

    @Test
    fun `an import that fails part way writes nothing`() = runBlocking {
        val set = SetLog(0, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 500)
        val session = LiftSession("s1", 0, 1_000, "Swings", listOf(set))
        val bell = 16.0
        // The second session's uid is the first's: SQLite refuses it after the bell and the first session went in.
        val import = LiftImport(sessions = listOf(session, session.copy(title = "Again")), bellsKg = listOf(bell))
        assertFailsWith<Exception> { repository.importAll(import) }

        assertEquals(emptyList(), database.sessionDao().allSets())
        assertEquals(emptyList(), database.sessionDao().observeSessions().first())
        assertEquals(emptyList(), database.bellDao().observeAll().first())
    }

    @Test
    fun `an import lands its sessions with their sets`() = runBlocking {
        val set = SetLog(0, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 500)
        repository.importAll(LiftImport(sessions = listOf(LiftSession("s1", 0, 1_000, "Swings", listOf(set)))))

        assertEquals(listOf("s1"), database.sessionDao().observeSessions().first().map { it.uid })
        assertEquals(listOf("s1"), database.sessionDao().allSets().map { it.sessionUid })
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
