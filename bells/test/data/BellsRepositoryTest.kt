package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.FeedCodec
import dev.justinsmith.core.sync.FeedSnapshot
import dev.justinsmith.core.sync.SessionDetail
import dev.justinsmith.core.sync.SessionRecord
import dev.justinsmith.core.sync.SetRecord
import dev.justinsmith.core.sync.SiblingFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SessionDao] over lists, as the real queries order them. */
private class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow(emptyList<SessionEntity>())
    val sets = MutableStateFlow(emptyList<SetEntity>())
    private var nextId = 1L

    override fun observeSessions(): Flow<List<SessionEntity>> =
        sessions.map { all -> all.filterNot { it.deleted }.sortedByDescending { it.startedAtEpochMillis } }

    override fun observeSets(): Flow<List<SetEntity>> = sets

    override suspend fun allSessions() = sessions.value

    override suspend fun allSets() = sets.value

    override suspend fun active() = sessions.value.firstOrNull { it.finishedAtEpochMillis == null && !it.deleted }

    override suspend fun insert(session: SessionEntity) = sessions.update { it + session }

    override suspend fun insert(set: SetEntity) = sets.update { it + set.copy(id = nextId++) }

    override suspend fun deleteSet(id: Long) = sets.update { all -> all.filterNot { it.id == id } }

    override suspend fun finish(uid: String, title: String, at: Long) = sessions.update { all ->
        all.map {
            if (it.uid ==
                uid
            ) {
                it.copy(finishedAtEpochMillis = at, title = title, updatedAtEpochMillis = at)
            } else {
                it
            }
        }
    }

    override suspend fun markDeleted(uid: String, now: Long) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(deleted = true, updatedAtEpochMillis = now) else it }
    }

    override suspend fun discard(uid: String) = sessions.update { all -> all.filterNot { it.uid == uid } }

    override suspend fun discardSets(uid: String) = sets.update { all -> all.filterNot { it.sessionUid == uid } }
}

private class FakeBellDao : BellDao {
    private val rows = MutableStateFlow(emptyList<BellEntity>())

    override fun observeAll(): Flow<List<BellEntity>> = rows.map { all -> all.sortedBy { it.kg } }

    override suspend fun upsert(bell: BellEntity) = rows.update { all -> all.filterNot { it.kg == bell.kg } + bell }

    override suspend fun delete(kg: Double) = rows.update { all -> all.filterNot { it.kg == kg } }
}

private class FakeSiblingDao : SiblingDao {
    private val rows = MutableStateFlow(emptyMap<String, SiblingEntity>())

    override fun observeAll(): Flow<List<SiblingEntity>> = rows.map { all ->
        all.values.sortedByDescending { it.finishedAtEpochMillis }
    }

    override suspend fun all() = rows.value.values.toList()

    override suspend fun upsert(session: SiblingEntity) = rows.update { it + (session.uid to session) }

    override suspend fun delete(uid: String) = rows.update { it - uid }
}

private class MemoryFeed : SiblingFeed {
    var published: String? = null
    var sibling: String? = null

    override fun publish(text: String) {
        published = text
    }

    override fun readSibling(): String? = sibling
}

class BellsRepositoryTest {
    private var now = 1_000L
    private val sessionDao = FakeSessionDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = BellsRepository(sessionDao, FakeBellDao(), FakeSiblingDao(), scope) { now }
    private val feed = MemoryFeed()
    private val sync = repository.siblingSync(feed, scope, io = Dispatchers.Unconfined)

    private fun published() = assertNotNull(feed.published?.let(FeedCodec::decode))

    private fun swingsAndGetUps() {
        repository.startSession()
        repository.logSet(Exercise.TwoHandSwing, 24.0, 10, Side.Left)
        now += 60_000
        repository.logSet(Exercise.GetUp, 20.0, 5, Side.Right)
        now += 60_000
        repository.finishSession("Swings and get-ups")
    }

    @Test
    fun `a session logs sets and finishes under its title`() = runTest {
        repository.startSession()
        assertNotNull(repository.active.value)
        repository.logSet(Exercise.TwoHandSwing, 24.0, 10, Side.Left)
        val set = repository.active.value?.sets?.single()
        assertEquals(Exercise.TwoHandSwing, set?.exercise)
        // A two-sided lift keeps no side.
        assertNull(set?.side)
        now += 600_000
        repository.finishSession("Swings")
        assertNull(repository.active.value)
        val done = repository.sessions.value.single()
        assertEquals("Swings", done.title)
        assertEquals(600, done.elapsedSeconds(0))
    }

    @Test
    fun `a session finished with no sets is dropped`() = runTest {
        repository.startSession()
        repository.finishSession("")
        assertTrue(repository.sessions.value.isEmpty())
        assertTrue(sessionDao.sessions.value.isEmpty())
    }

    @Test
    fun `a finished session is published to Interval with its sets`() = runTest {
        swingsAndGetUps()
        val record = published().sessions.single()
        assertEquals(AppId.Bells, record.source)
        assertEquals("Swings and get-ups", record.title)
        assertEquals(120, record.elapsedSeconds)
        assertEquals("Kettlebell", record.activity)
        assertEquals(
            SessionDetail.Kettlebell(
                listOf(SetRecord("TwoHandSwing", 24.0, 10), SetRecord("GetUp", 20.0, 5, "Right")),
                340.0,
            ),
            record.typedDetail(),
        )
    }

    @Test
    fun `the session in progress is not published`() = runTest {
        repository.startSession()
        repository.logSet(Exercise.Press, 16.0, 5, Side.Left)
        assertTrue(published().sessions.isEmpty())
    }

    @Test
    fun `a deleted session is published as a tombstone`() = runTest {
        swingsAndGetUps()
        repository.deleteSession(repository.sessions.value.single().uid)
        assertTrue(repository.sessions.value.isEmpty())
        assertTrue(published().sessions.single().deleted)
    }

    @Test
    fun `Interval's workouts are taken in on a pull`() = runTest {
        val tabata = SessionRecord(
            uid = "i1",
            source = AppId.Interval,
            finishedAtEpochMillis = 500,
            elapsedSeconds = 240,
            title = "Tabata",
            updatedAtEpochMillis = 1,
        )
        feed.sibling = FeedCodec.encode(FeedSnapshot(source = AppId.Interval, sessions = listOf(tabata)))
        sync.pull()
        assertEquals(listOf(IntervalWorkout("i1", 500, 240, "Tabata")), repository.interval.value)
    }
}
