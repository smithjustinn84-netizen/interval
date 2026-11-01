package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.HoldClock
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.RecordingBeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HoldTimerTest {
    private val hand = HandTime()
    private val beeper = RecordingBeeper()
    private val logged = mutableListOf<Pair<SetDraft, Int>>()
    private val timer = HoldTimer(hand.clock, beeper) { set, seconds -> logged += set to seconds }

    private val plank = SetDraft(exercise = BuiltInExercise.Plank, seconds = 20)

    @Test
    fun `a hold counts in with beeps and starts on the last`() {
        timer.start(plank)
        hand.now = 1_000
        timer.tick(hand.now)
        assertEquals(listOf(EmomCue.Countdown), beeper.cues)
        hand.now = HoldClock.COUNT_IN_MILLIS
        timer.tick(hand.now)
        assertEquals(EmomCue.Start, beeper.cues.last())
    }

    @Test
    fun `a hold run to its end logs its seconds and is over`() {
        timer.start(plank)
        hand.now = HoldClock.COUNT_IN_MILLIS + 20_000
        timer.tick(hand.now)
        assertEquals(listOf(plank to 20), logged)
        assertEquals(EmomCue.Finished, beeper.cues.last())
        assertNull(timer.state.value.run)
    }

    @Test
    fun `a hold stopped early logs the whole seconds held`() {
        timer.start(plank)
        hand.now = HoldClock.COUNT_IN_MILLIS + 12_400
        timer.stop()
        assertEquals(listOf(plank to 12), logged)
    }

    @Test
    fun `a hold stopped in its count-in or cancelled logs nothing`() {
        timer.start(plank)
        hand.now = 1_000
        timer.stop()
        timer.start(plank)
        hand.now = HoldClock.COUNT_IN_MILLIS + 5_000
        timer.cancel()
        assertEquals(emptyList(), logged)
        assertNull(timer.state.value.run)
    }

    @Test
    fun `the first side of a one-sided hold offers the other`() {
        val side = SetDraft(exercise = BuiltInExercise.SidePlank, side = Side.Left, seconds = 20)
        timer.start(side)
        hand.now = HoldClock.COUNT_IN_MILLIS + 20_000
        timer.tick(hand.now)
        assertEquals(HoldOffer(BuiltInExercise.SidePlank, Side.Right), timer.state.value.offer)
        timer.start(side.copy(side = Side.Right))
        assertEquals(false, timer.state.value.run?.firstSide)
    }
}
