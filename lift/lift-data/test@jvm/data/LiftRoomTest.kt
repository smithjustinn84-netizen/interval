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
import kotlin.test.assertNull

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
        clock = { 1_000L },
    )

    @AfterTest
    fun close() {
        scope.cancel()
        database.close()
    }

    private fun <T> await(read: () -> T, until: (T) -> Boolean): T = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            repository.sessions.active.first { until(read()) }
            read()
        }
    }

    @Test
    fun `a set removed and put back keeps its place and its id`() {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, null)
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 5, null)
        val sets = await({ repository.sessions.active.value?.sets.orEmpty() }) { it.size == 2 }
        val first = sets.first()
        repository.sets.removeSet(first.id)
        await({ repository.sessions.active.value?.sets.orEmpty() }) { it.size == 1 }
        repository.sets.restoreSet(first)
        val back = await({ repository.sessions.active.value?.sets.orEmpty() }) { it.size == 2 }
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

    @Test
    fun `discarding the session in progress takes its sets with it`() = runBlocking {
        val sessions = database.sessionDao()
        sessions.insert(SessionEntity("s1", 0, null, "", updatedAtEpochMillis = 0))
        sessions.insert(swing("s1"))
        sessions.discardActive()

        assertEquals(emptyList(), sessions.allSets())
        assertEquals(emptyList(), sessions.observeSessions().first())
    }

    @Test
    fun `finishing drops a session with no sets and keeps one with sets`() = runBlocking {
        val sessions = database.sessionDao()
        sessions.insert(SessionEntity("empty", 0, null, "", updatedAtEpochMillis = 0))
        sessions.finishActive("Nothing", 500)
        assertEquals(emptyList(), sessions.observeSessions().first())

        sessions.insert(SessionEntity("s1", 0, null, "", updatedAtEpochMillis = 0))
        sessions.insert(swing("s1"))
        sessions.finishActive("Swings", 500)
        val finished = sessions.observeSessions().first().single()
        assertEquals("Swings", finished.title)
        assertEquals(500L, finished.finishedAtEpochMillis)
    }

    @Test
    fun `a band done with is archived and one never used is deleted`() = runBlocking {
        val bands = database.bandDao()
        val used = BandEntity("used", "Loop", "Red", 5, archived = true, updatedAtEpochMillis = 0)
        val unused = used.copy(id = "unused", position = 6)
        bands.upsert(listOf(used.copy(archived = false), unused.copy(archived = false)))
        database.sessionDao().insert(SessionEntity("s1", 0, null, "", updatedAtEpochMillis = 0))
        database.sessionDao().insert(swing("s1").copy(exercise = "BandPullApart", band = "used"))
        bands.removeOrArchive(used)
        bands.removeOrArchive(unused)

        assertEquals(listOf(used), bands.observeAll().first())
    }

    @Test
    fun `deleting the program followed stops following it`() = runBlocking {
        val programs = database.customProgramDao()
        programs.upsert(CustomProgramEntity("abc", "Mine", 3, 4, "[]", 0))
        database.followingDao().upsert(FollowingEntity(programId = "custom:abc", startedAtEpochMillis = 0))
        programs.deleteProgram("abc", "custom:abc")

        assertEquals(emptyList(), programs.observeAll().first())
        assertNull(database.followingDao().observe().first())
    }

    private fun swing(session: String) =
        SetEntity(0, session, "TwoHandSwing", 24.0, 10, side = null, loggedAtEpochMillis = 100)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
