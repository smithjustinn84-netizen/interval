package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.fakes.FakeBellDao
import dev.justinsmith.bells.fakes.FakeFollowingDao
import dev.justinsmith.bells.fakes.FakeSessionDao
import dev.justinsmith.bells.fakes.FakeSiblingDao
import dev.justinsmith.bells.fakes.MemoryFeed
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.FeedCodec
import dev.justinsmith.core.sync.FeedSnapshot
import dev.justinsmith.core.sync.SessionDetail
import dev.justinsmith.core.sync.SessionRecord
import dev.justinsmith.core.sync.SetRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BellsRepositoryTest {
    private var now = 1_000L
    private val sessionDao = FakeSessionDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository =
        BellsRepository(sessionDao, FakeBellDao(), FakeSiblingDao(), FakeFollowingDao(), scope) { now }
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

    @Test
    fun `following a program counts its days by the sessions of it finished`() = runTest {
        assertNull(repository.programDay.value)
        repository.follow(ProgramId.PressLadders)
        assertEquals(ProgramId.PressLadders, repository.following.value)
        assertEquals(1, repository.programDay.value?.number)
        // A free session is not a day of it.
        swingsAndGetUps()
        assertEquals(1, repository.programDay.value?.number)
        repository.startSession(ProgramId.PressLadders)
        assertEquals(ProgramId.PressLadders, repository.active.value?.programId)
        repository.logSet(Exercise.Press, 16.0, 1, Side.Left)
        repository.finishSession("Press")
        assertEquals(2, repository.programDay.value?.number)
        repository.stopFollowing()
        assertNull(repository.programDay.value)
    }
}
