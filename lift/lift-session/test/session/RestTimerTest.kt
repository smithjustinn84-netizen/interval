package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.fakes.RecordingBeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestTimerTest {
    private val hand = HandTime(1_000L)
    private val beeper = RecordingBeeper()
    private val timer = RestTimer(hand.clock, beeper)

    @Test
    fun `a rest beeps into its end and is over`() {
        timer.start(30)
        assertEquals(RestRun(1_000, 30, HandTime.EPOCH + 1_000), timer.state.value)
        hand.now += 27_000
        timer.tick(hand.now)
        assertEquals(listOf(EmomCue.Countdown), beeper.cues)
        hand.now += 3_000
        timer.tick(hand.now)
        assertEquals(listOf(EmomCue.Countdown, EmomCue.Start), beeper.cues)
        assertNull(timer.state.value)
    }

    @Test
    fun `a rest made longer runs on from when it started`() {
        timer.start(30)
        hand.now += 20_000
        timer.tick(hand.now)
        timer.adjust(45)
        assertEquals(RestRun(1_000, 45, HandTime.EPOCH + 1_000), timer.state.value)
        hand.now += 20_000
        timer.tick(hand.now)
        assertEquals(45, timer.state.value?.seconds)
    }

    @Test
    fun `a rest made shorter than it has run is over without a beep`() {
        timer.start(60)
        hand.now += 40_000
        timer.tick(hand.now)
        timer.adjust(30)
        timer.tick(hand.now)
        assertNull(timer.state.value)
        assertEquals(emptyList(), beeper.cues)
    }

    @Test
    fun `a rest skipped is over without a beep`() {
        timer.start(30)
        timer.skip()
        hand.now += 30_000
        timer.tick(hand.now)
        assertNull(timer.state.value)
        assertEquals(emptyList(), beeper.cues)
    }
}
