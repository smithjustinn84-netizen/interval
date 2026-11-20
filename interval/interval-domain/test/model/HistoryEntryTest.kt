package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.IntervalConfig
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

    private val minutes = IntervalConfig(prepSeconds = 0, workSeconds = 60, restSeconds = 60, rounds = 3)

    /** [minutes] run to its end, taking [wallSeconds] on the wall for [elapsedSeconds] on its clock. */
    private fun ran(
        elapsedSeconds: Int = minutes.totalSeconds,
        wallSeconds: Int = elapsedSeconds,
        endedEarly: Boolean = false,
    ) = HistoryEntry(
        finishedAtEpochMillis = start + wallSeconds * 1_000L,
        config = minutes,
        elapsedSeconds = elapsedSeconds,
        endedEarly = endedEarly,
        workActiveSeconds = 180,
        startedAtEpochMillis = start,
    )

    @Test
    fun `a workout run straight through has a rest between each two works`() {
        assertEquals(listOf(60 until 120, 180 until 240), ran().rests())
    }

    @Test
    fun `a paused workout says nothing of where its rests fell`() {
        assertNull(ran(wallSeconds = minutes.totalSeconds + 40).rests())
    }

    @Test
    fun `a workout skipped through or ended early says nothing either`() {
        assertNull(ran(elapsedSeconds = minutes.totalSeconds - 30).rests())
        assertNull(ran(elapsedSeconds = 130, endedEarly = true).rests())
    }

    @Test
    fun `a workout logged by hand has no rests to read`() {
        assertNull(HistoryEntry.loggedByHand(minutes, start).rests())
    }
}
