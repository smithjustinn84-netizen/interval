package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.snatchIntervals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `a count is read back only when both halves make sense`() {
        assertEquals(SnatchCount(8, 16.0), SnatchCount.of(8, 16.0))
        assertNull(SnatchCount.of(8, null))
        assertNull(SnatchCount.of(null, 16.0))
        assertNull(SnatchCount.of(0, 16.0))
        assertNull(SnatchCount.of(8, -1.0))
    }
}
