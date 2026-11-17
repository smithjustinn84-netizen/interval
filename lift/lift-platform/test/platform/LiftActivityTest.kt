package dev.justinsmith.lift.platform

import dev.justinsmith.interval.platform.LiveActivitySegment
import dev.justinsmith.interval.platform.LiveActivitySpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiftActivityTest {
    private val start = 1_000_000L

    @Test
    fun `a rest counts down to its end`() {
        val activity = liftActivity(TimerRun.Rest(start, start + 90_000), "Resting", "Rest over")
        assertEquals("Resting", activity.title)
        assertEquals("Rest over", activity.staleTitle)
        assertEquals(LiveActivitySpan(start, start + 90_000), activity.interval)
        assertNull(activity.run)
        assertEquals(listOf(LiveActivitySegment(90)), activity.segments)
    }

    @Test
    fun `an EMOM is its count-in then a segment a minute`() {
        val activity = liftActivity(TimerRun.Emom(start, minutes = 3), "EMOM", "EMOM done")
        assertEquals(LiveActivitySpan(start, start + 190_000), activity.interval)
        assertEquals(listOf(10, 60, 60, 60), activity.segments.map { it.length })
    }

    @Test
    fun `a density block is its count-in then its cap`() {
        val activity = liftActivity(TimerRun.Density(start, minutes = 10), "Rounds", "Time")
        assertEquals(start + 610_000, activity.interval?.endMillis)
        assertEquals(listOf(10, 600), activity.segments.map { it.length })
    }

    @Test
    fun `a hold is its count-in then its seconds`() {
        val activity = liftActivity(TimerRun.Hold(start, seconds = 30), "Holding", "Hold done")
        assertEquals(start + 33_000, activity.interval?.endMillis)
        assertEquals(listOf(3, 30), activity.segments.map { it.length })
    }
}
