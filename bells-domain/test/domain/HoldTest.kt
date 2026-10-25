package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HoldTest {
    private val clock = HoldClock(seconds = 30, countInMillis = 3_000)

    @Test
    fun `it counts in then holds then is done`() {
        assertEquals(HoldState(HoldPhase.GetReady, 3, 0f), clock.at(0))
        assertEquals(HoldPhase.GetReady to 1, clock.at(2_500).let { it.phase to it.secondsLeft })
        assertEquals(HoldPhase.Hold to 30, clock.at(3_000).let { it.phase to it.secondsLeft })
        assertEquals(HoldPhase.Hold to 30, clock.at(3_400).let { it.phase to it.secondsLeft })
        assertEquals(HoldPhase.Hold to 1, clock.at(32_999).let { it.phase to it.secondsLeft })
        assertEquals(HoldState(HoldPhase.Done, 0, 1f), clock.at(33_000))
        assertEquals(0.5f, clock.at(18_000).progress)
    }

    @Test
    fun `it counts the whole seconds held and none in the count-in`() {
        assertEquals(0, clock.heldSeconds(2_000))
        assertEquals(0, clock.heldSeconds(3_999))
        assertEquals(12, clock.heldSeconds(15_400))
        assertEquals(30, clock.heldSeconds(60_000))
    }

    @Test
    fun `cues count into the hold and out of it`() {
        // From before it started, so the count-in's first beep at 0 plays.
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Start, clock.cuesBetween(-1, 3_000))
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Finished, clock.cuesBetween(29_000, 33_000))
        assertEquals(emptyList(), clock.cuesBetween(3_000, 29_999))
        assertEquals(listOf(EmomCue.Finished), clock.cuesBetween(32_000, 40_000))
    }
}
