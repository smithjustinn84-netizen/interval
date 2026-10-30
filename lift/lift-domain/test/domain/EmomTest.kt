package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EmomTest {
    private val clock = EmomClock(minutes = 3, countInMillis = 10_000)

    @Test
    fun `it counts in then works each minute until the set is done`() {
        assertEquals(EmomPhase.GetReady, clock.at(0, emptySet()).phase)
        assertEquals(10, clock.at(0, emptySet()).secondsLeft)
        val work = clock.at(10_000 + 5_000, emptySet())
        assertEquals(EmomPhase.Work to 1, work.phase to work.minute)
        assertEquals(55, work.secondsLeft)
        assertEquals(EmomPhase.Rest, clock.at(10_000 + 20_000, setOf(1)).phase)
        assertEquals(2, clock.at(10_000 + 61_000, setOf(1)).minute)
        assertEquals(EmomPhase.Work, clock.at(10_000 + 61_000, setOf(1)).phase)
    }

    @Test
    fun `it is done when the minutes run out`() {
        assertEquals(EmomPhase.Done, clock.at(10_000 + 180_000, setOf(1, 2, 3)).phase)
    }

    @Test
    fun `cues count down into each minute and sound its start and the end`() {
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Start, clock.cuesBetween(6_000, 10_000))
        assertEquals(
            listOf(EmomCue.Countdown, EmomCue.Countdown, EmomCue.Countdown, EmomCue.Finished),
            clock.cuesBetween(186_000, 190_000),
        )
        assertEquals(emptyList(), clock.cuesBetween(20_000, 30_000))
    }
}
