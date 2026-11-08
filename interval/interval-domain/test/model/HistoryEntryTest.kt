package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.snatchIntervals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryEntryTest {
    private val start = 1_000_000L

    private fun fifteenFifteen(elapsedSeconds: Int, endedEarly: Boolean) = HistoryEntry(
        finishedAtEpochMillis = start + elapsedSeconds * 1_000L,
        config = snatchIntervals.config,
        elapsedSeconds = elapsedSeconds,
        endedEarly = endedEarly,
        startedAtEpochMillis = start,
    )

    @Test
    fun `15-15 run out ends forty work intervals thirty seconds apart`() {
        val ends = fifteenFifteen(elapsedSeconds = 1_195, endedEarly = false).workEnds()

        assertEquals(40, ends.size)
        assertEquals(start + 25_000L, ends.first())
        assertEquals(List(39) { 30_000L }, ends.zipWithNext { a, b -> b - a })
    }

    @Test
    fun `one ended at five minutes holds only the work done by then`() {
        val ends = fifteenFifteen(elapsedSeconds = 300, endedEarly = true).workEnds()

        assertEquals(10, ends.size)
        assertEquals(start + 295_000L, ends.last())
    }

    @Test
    fun `a workout logged by hand runs the whole course back from its finish`() {
        val finished = 2_000_000L
        val logged = HistoryEntry.loggedByHand(snatchIntervals.config, finished)

        assertEquals(snatchIntervals.config.totalSeconds, logged.elapsedSeconds)
        assertFalse(logged.endedEarly)
        assertNull(logged.workActiveSeconds)
        assertEquals(finished - logged.elapsedSeconds * 1_000L, logged.startedAt)
        assertEquals(40, logged.workEnds().size)
        assertTrue(logged.loggedByHand)
        // A timed run keeps its work time; an older entry kept neither its work time nor its start.
        assertFalse(fifteenFifteen(1_195, endedEarly = false).copy(workActiveSeconds = 600).loggedByHand)
        assertFalse(HistoryEntry(finished, snatchIntervals.config, 1_195, endedEarly = false).loggedByHand)
    }

    @Test
    fun `a count is read back only when both halves make sense`() {
        assertEquals(SnatchCount(8, 16.0), SnatchCount.of(8, 16.0))
        assertNull(SnatchCount.of(8, null))
        assertNull(SnatchCount.of(null, 16.0))
        assertNull(SnatchCount.of(0, 16.0))
        assertNull(SnatchCount.of(8, -1.0))
    }
}
