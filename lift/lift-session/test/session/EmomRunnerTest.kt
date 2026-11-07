package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingVoice
import dev.justinsmith.lift.platform.Coaching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmomRunnerTest {
    private val hand = HandTime()
    private val beeper = RecordingBeeper()
    private val logged = mutableListOf<Pair<Side?, Int>>()
    private val voice = RecordingVoice()
    private val runner = EmomRunner(hand.clock, beeper, voice, { Coaching.Fault(it, 0) }) { run, minute ->
        logged += run.sideOf(minute) to minute
    }

    private val workOfMinuteOne = EmomClock.COUNT_IN_MILLIS + 1_000

    @Test
    fun `a set done in the count-in logs nothing`() {
        runner.start(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        runner.setDone()
        assertEquals(emptyList<Pair<Side?, Int>>(), logged)
    }

    @Test
    fun `each minute's set logs once on its side and the minute rests after it`() {
        runner.start(BuiltInExercise.Press, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        hand.now = workOfMinuteOne
        runner.setDone()
        runner.setDone()
        hand.now += EmomClock.MINUTE_MILLIS
        runner.setDone()
        assertEquals(listOf<Pair<Side?, Int>>(Side.Left to 1, Side.Right to 2), logged)
        assertEquals(setOf(1, 2), runner.state.value?.done)
    }

    @Test
    fun `ticks beep the count-in and each minute and the run ends once its minutes are up`() {
        runner.start(BuiltInExercise.TwoHandSwing, Load.Bell(24.0), 10, minutes = 1, firstSide = Side.Left)
        hand.now = workOfMinuteOne
        runner.tick(hand.now)
        assertEquals(EmomCue.Start, beeper.cues.last())
        hand.now = EmomClock(1).lengthMillis + EmomClock.DONE_HOLD_MILLIS
        runner.tick(hand.now)
        assertEquals(EmomCue.Finished, beeper.cues.last())
        assertNull(runner.state.value)
    }

    @Test
    fun `each minute's count-in says the next of the lift's cues and a set done says a fault`() {
        val swing = BuiltInExercise.TwoHandSwing
        runner.start(swing, Load.Bell(24.0), 10, minutes = 3, firstSide = Side.Left)
        hand.now = 2_000
        runner.tick(hand.now)
        assertEquals(listOf<Coaching>(Coaching.Cue(swing, 0)), voice.lines)
        hand.now = workOfMinuteOne
        runner.setDone()
        hand.now = EmomClock.COUNT_IN_MILLIS + EmomClock.MINUTE_MILLIS - 5_000
        runner.tick(hand.now)
        assertEquals(listOf(Coaching.Cue(swing, 0), Coaching.Fault(swing, 0), Coaching.Cue(swing, 1)), voice.lines)
    }

    @Test
    fun `no count-in line while the minute before has no set done`() {
        runner.start(BuiltInExercise.Snatch, Load.Bell(16.0), 5, minutes = 3, firstSide = Side.Left)
        hand.now = workOfMinuteOne
        runner.tick(hand.now)
        hand.now = EmomClock.COUNT_IN_MILLIS + EmomClock.MINUTE_MILLIS - 5_000
        runner.tick(hand.now)
        assertEquals(listOf<Coaching>(Coaching.Cue(BuiltInExercise.Snatch, 0)), voice.lines)
    }
}
