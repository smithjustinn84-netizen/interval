package dev.justinsmith.interval.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.FeedCodec
import dev.justinsmith.core.sync.FeedSnapshot
import dev.justinsmith.core.sync.SessionDetail
import dev.justinsmith.core.sync.SessionRecord
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SiblingSyncTest {
    /** Holds what this app published and what the sibling has, in memory. */
    private class MemoryFeed(var sibling: String? = null) : SiblingFeed {
        var published: String? = null

        override fun publish(text: String) {
            published = text
        }

        override fun readSibling(): String? = sibling
    }

    private val storage = FakeStorage()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = storage.repository(now = { 1_000L }, scope = scope)
    private val feed = MemoryFeed()
    private val sync = repository.siblingSync(feed, scope, io = Dispatchers.Unconfined)

    private fun published(): FeedSnapshot = assertNotNull(feed.published?.let(FeedCodec::decode))

    private fun bells(uid: String, updated: Long = 1, deleted: Boolean = false) = SessionRecord(
        uid = uid,
        source = AppId.Bells,
        finishedAtEpochMillis = 500,
        elapsedSeconds = 1_570,
        activity = Activity.Kettlebell.name,
        title = "Swings and get-ups",
        updatedAtEpochMillis = updated,
        deleted = deleted,
    )

    private fun bellsFeed(vararg sessions: SessionRecord) =
        FeedCodec.encode(FeedSnapshot(source = AppId.Bells, sessions = sessions.toList()))

    @Test
    fun `a workout recorded here is published under its preset's name`() = runTest {
        repository.recordWorkout(IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8), 240)
        val session = published().sessions.single()
        assertEquals(AppId.Interval, session.source)
        assertEquals("Tabata", session.title)
        assertEquals(240, session.elapsedSeconds)
        assertEquals(SessionDetail.Interval(10, 20, 10, 8), session.typedDetail())
    }

    @Test
    fun `a deleted workout is published as a tombstone`() = runTest {
        repository.recordWorkout(IntervalConfig(), 60)
        repository.deleteWorkout(assertNotNull(repository.history.value.single().id))
        assertTrue(published().sessions.single().deleted)
    }

    @Test
    fun `the sibling's workouts are taken in on a pull`() = runTest {
        feed.sibling = bellsFeed(bells("a"), bells("b", deleted = true))
        sync.pull()
        val workout = repository.siblings.value.single()
        assertEquals("a", workout.uid)
        assertEquals(AppId.Bells, workout.source)
        assertEquals(Activity.Kettlebell, workout.activity)
    }

    @Test
    fun `a workout the sibling deleted goes on the next pull`() = runTest {
        feed.sibling = bellsFeed(bells("a"))
        sync.pull()
        feed.sibling = bellsFeed(bells("a", updated = 2, deleted = true))
        sync.pull()
        assertTrue(repository.siblings.value.isEmpty())
    }

    @Test
    fun `this app's own feed is never taken in`() = runTest {
        repository.recordWorkout(IntervalConfig(), 60)
        feed.sibling = feed.published
        sync.pull()
        assertTrue(repository.siblings.value.isEmpty())
    }

    @Test
    fun `an unreadable feed changes nothing`() = runTest {
        feed.sibling = bellsFeed(bells("a"))
        sync.pull()
        feed.sibling = "{ not a feed"
        sync.pull()
        assertEquals(listOf("a"), repository.siblings.value.map { it.uid })
    }
}
