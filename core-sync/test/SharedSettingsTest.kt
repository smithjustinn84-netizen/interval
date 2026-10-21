package dev.justinsmith.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedSettingsTest {
    private fun from(settings: SharedSettings?, source: AppId = AppId.Interval) =
        FeedSnapshot(source = source, sessions = emptyList(), settings = settings)

    private val mine = SharedSettings(bodyWeightKg = 80.0, weeklyGoal = 3, updatedAtEpochMillis = 100)

    @Test
    fun `the sibling's settings win when changed later`() {
        val theirs = SharedSettings(bodyWeightKg = 75.0, weeklyGoal = 4, updatedAtEpochMillis = 200)
        assertEquals(theirs, newerSettings(mine, from(theirs), AppId.Interval))
    }

    @Test
    fun `older or equally old settings are kept out`() {
        assertNull(newerSettings(mine, from(mine.copy(weeklyGoal = 5, updatedAtEpochMillis = 50)), AppId.Interval))
        // The same change coming back from the sibling.
        assertNull(newerSettings(mine, from(mine.copy(weeklyGoal = 5)), AppId.Interval))
    }

    @Test
    fun `any settings beat none and none change nothing`() {
        assertEquals(mine, newerSettings(null, from(mine), AppId.Interval))
        assertNull(newerSettings(mine, from(null), AppId.Interval))
    }

    @Test
    fun `only the expected app's settings are taken`() {
        val later = mine.copy(updatedAtEpochMillis = 500)
        assertNull(newerSettings(mine, from(later, source = AppId.Bells), AppId.Interval))
    }

    @Test
    fun `a goal out of range is clamped and a weight that isn't one dropped`() {
        val odd = SharedSettings(bodyWeightKg = -1.0, weeklyGoal = 40, updatedAtEpochMillis = 300)
        assertEquals(
            SharedSettings(bodyWeightKg = null, weeklyGoal = 14, updatedAtEpochMillis = 300),
            newerSettings(mine, from(odd), AppId.Interval),
        )
    }

    @Test
    fun `settings survive the codec and an older snapshot without them still reads`() {
        val snapshot = from(mine)
        assertEquals(snapshot, FeedCodec.decode(FeedCodec.encode(snapshot)))
        val old = """{"schemaVersion":1,"source":"interval","sessions":[]}"""
        assertNull(FeedCodec.decode(old)?.settings)
    }
}
