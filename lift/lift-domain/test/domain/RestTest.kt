package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestTest {
    private val clock = RestClock(seconds = 60)

    @Test
    fun `it counts down from its length and stops at none left`() {
        assertEquals(RestState(60, 60, 0f), clock.at(0))
        assertEquals(60, clock.at(400).secondsLeft)
        assertEquals(1, clock.at(59_999).secondsLeft)
        assertEquals(0.5f, clock.at(30_000).progress)
        assertEquals(RestState(60, 0, 1f), clock.at(90_000))
    }

    @Test
    fun `it is done once its length has passed`() {
        assertFalse(clock.isDone(59_999))
        assertTrue(clock.isDone(60_000))
    }

    @Test
    fun `cues count into the end and start the next set`() {
        assertEquals(emptyList(), clock.cuesBetween(0, 56_999))
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Start, clock.cuesBetween(56_999, 60_000))
        assertEquals(listOf(EmomCue.Start), clock.cuesBetween(59_500, 70_000))
        assertEquals(emptyList(), clock.cuesBetween(60_000, 70_000))
    }
}
