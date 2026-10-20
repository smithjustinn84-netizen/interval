package dev.justinsmith.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MirrorTest {
    private fun session(uid: String, updated: Long = 1, deleted: Boolean = false, source: AppId = AppId.Bells) =
        SessionRecord(
            uid = uid,
            source = source,
            finishedAtEpochMillis = updated,
            elapsedSeconds = 600,
            title = "Swings",
            updatedAtEpochMillis = updated,
            deleted = deleted,
        )

    private fun snapshot(vararg sessions: SessionRecord, source: AppId = AppId.Bells) = FeedSnapshot(
        source = source,
        sessions = sessions.toList(),
    )

    @Test
    fun `a new workout is written`() {
        val plan = mirror(emptyMap(), snapshot(session("a")), AppId.Bells)
        assertEquals(listOf("a"), plan.upserts.map { it.uid })
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `a workout changed since is written again and an unchanged one is left`() {
        val plan =
            mirror(
                mapOf("a" to 1L, "b" to 5L),
                snapshot(session("a", updated = 2), session("b", updated = 5)),
                AppId.Bells,
            )
        assertEquals(listOf("a"), plan.upserts.map { it.uid })
    }

    @Test
    fun `an older copy than the one kept is ignored`() {
        val plan = mirror(mapOf("a" to 9L), snapshot(session("a", updated = 2)), AppId.Bells)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a tombstone drops the local copy`() {
        val plan = mirror(mapOf("a" to 1L), snapshot(session("a", updated = 2, deleted = true)), AppId.Bells)
        assertEquals(listOf("a"), plan.deletes)
        assertTrue(plan.upserts.isEmpty())
    }

    @Test
    fun `a workout the sibling no longer has is dropped`() {
        val plan = mirror(mapOf("gone" to 1L), snapshot(session("a")), AppId.Bells)
        assertEquals(listOf("gone"), plan.deletes)
    }

    @Test
    fun `a snapshot from the wrong app changes nothing`() {
        val plan = mirror(mapOf("a" to 1L), snapshot(session("b"), source = AppId.Interval), AppId.Bells)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a snapshot round-trips through its text`() {
        val detail = SessionDetail.Kettlebell(listOf(SetRecord("Swing", 24.0, 10)), tonnageKg = 240.0)
        val original = snapshot(session("a").copy(detail = detail.toJson(), activity = "Kettlebell"))
        val decoded = FeedCodec.decode(FeedCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(detail, decoded?.sessions?.single()?.typedDetail())
    }

    @Test
    fun `the schema version is always written`() {
        val text = FeedCodec.encode(FeedSnapshot(source = AppId.Interval, sessions = emptyList()))
        assertTrue("\"schemaVersion\":${FeedCodec.SCHEMA_VERSION}" in text, text)
    }

    @Test
    fun `a newer schema or unreadable text decodes to null`() {
        val newer = FeedCodec.encode(snapshot(session("a")).copy(schemaVersion = FeedCodec.SCHEMA_VERSION + 1))
        assertNull(FeedCodec.decode(newer))
        assertNull(FeedCodec.decode("not json"))
    }

    @Test
    fun `a detail of an unknown kind still lists and reads as null`() {
        val withUnknown = FeedCodec.encode(snapshot(session("a")))
            .replace("\"title\":\"Swings\"", "\"title\":\"Swings\",\"detail\":{\"kind\":\"rowing\",\"meters\":2000}")
        assertTrue("rowing" in withUnknown)
        val decoded = FeedCodec.decode(withUnknown)?.sessions?.single()
        assertEquals("Swings", decoded?.title)
        assertNull(decoded?.typedDetail())
    }
}
