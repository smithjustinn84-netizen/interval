package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DensityClockTest {
    private val clock = DensityClock(minutes = 5, countInMillis = 10_000)

    @Test
    fun `it counts in then counts the cap down with the rounds done`() {
        val ready = clock.at(0, rounds = 0)
        assertEquals(DensityPhase.GetReady to 10, ready.phase to ready.secondsLeft)
        val work = clock.at(10_000 + 60_500, rounds = 3)
        assertEquals(DensityPhase.Work, work.phase)
        assertEquals(240, work.secondsLeft)
        assertEquals(3, work.rounds)
        assertEquals(60_500f / 300_000f, work.progress)
    }

    @Test
    fun `there are no minutes to rest out once a round is done`() {
        // An EMOM rests out the minute once its set is done; the cap only counts down.
        assertEquals(DensityPhase.Work, clock.at(10_000 + 20_000, rounds = 1).phase)
        assertEquals(DensityPhase.Work, clock.at(10_000 + 299_999, rounds = 14).phase)
    }

    @Test
    fun `it is done when the cap runs out and over a moment later`() {
        val done = clock.at(10_000 + 300_000, rounds = 14)
        assertEquals(DensityPhase.Done to 0, done.phase to done.secondsLeft)
        assertEquals(14, done.rounds)
        assertEquals(310_000, clock.lengthMillis)
        assertFalse(clock.isOver(310_000))
        assertTrue(clock.isOver(310_000 + EmomClock.DONE_HOLD_MILLIS))
    }

    @Test
    fun `cues count in and sound the start`() {
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Start, clock.cuesBetween(6_000, 10_000))
    }

    @Test
    fun `the cap's last ten seconds sound at ten then three two one and the end`() {
        assertEquals(listOf(EmomCue.Countdown), clock.cuesBetween(295_000, 300_000))
        assertEquals(List(3) { EmomCue.Countdown } + EmomCue.Finished, clock.cuesBetween(306_000, 310_000))
    }

    @Test
    fun `nothing sounds between the start and the last ten seconds`() {
        // A minute in plays nothing, as an EMOM's would.
        assertEquals(emptyList(), clock.cuesBetween(10_000, 299_000))
        assertEquals(emptyList(), clock.cuesBetween(20_000, 20_000))
    }
}
