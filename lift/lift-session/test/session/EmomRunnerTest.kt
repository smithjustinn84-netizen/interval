package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.RecordingBeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmomRunnerTest {
    private var now = 0L
    private val beeper = RecordingBeeper()
    private val logged = mutableListOf<Pair<Side?, Int>>()
    private val runner = EmomRunner({ now }, beeper) { run, minute -> logged += run.sideOf(minute) to minute }

    private val workOfMinuteOne = EmomClock.COUNT_IN_MILLIS + 1_000

    @Test
    fun `a set done in the count-in logs nothing`() {
        runner.start(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
        runner.setDone()
        assertEquals(emptyList<Pair<Side?, Int>>(), logged)
    }

    @Test
    fun `each minute's set logs once on its side and the minute rests after it`() {
        runner.start(BuiltInExercise.Press, 16.0, 5, minutes = 3, firstSide = Side.Left, double = false)
        now = workOfMinuteOne
        runner.setDone()
        runner.setDone()
        now += EmomClock.MINUTE_MILLIS
        runner.setDone()
        assertEquals(listOf<Pair<Side?, Int>>(Side.Left to 1, Side.Right to 2), logged)
        assertEquals(setOf(1, 2), runner.state.value?.done)
    }

    @Test
    fun `ticks beep the count-in and each minute and the run ends once its minutes are up`() {
        runner.start(BuiltInExercise.TwoHandSwing, 24.0, 10, minutes = 1, firstSide = Side.Left, double = false)
        now = workOfMinuteOne
        runner.tick(now)
        assertEquals(EmomCue.Start, beeper.cues.last())
        now = EmomClock(1).lengthMillis + EmomClock.DONE_HOLD_MILLIS
        runner.tick(now)
        assertEquals(EmomCue.Finished, beeper.cues.last())
        assertNull(runner.state.value)
    }
}
