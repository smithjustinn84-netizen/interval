package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DensityCall
import dev.justinsmith.lift.domain.DensityClock
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

class DensityRunnerTest {
    private val hand = HandTime()
    private val beeper = RecordingBeeper()
    private val logged = mutableListOf<Pair<Side?, Int>>()
    private val voice = RecordingVoice()
    private val runner = DensityRunner(hand.clock, beeper, voice) { run, round -> logged += run.sideOf(round) to round }

    private val snatch = BuiltInExercise.Snatch
    private val inTheCap = EmomClock.COUNT_IN_MILLIS + 1_000

    private fun start(minutes: Int = 5) = runner.start(snatch, Load.Bell(16.0), 5, minutes, Side.Left, best = 14)

    @Test
    fun `a round tapped in the count-in logs nothing`() {
        start()
        runner.roundDone()
        assertEquals(emptyList(), logged)
        assertEquals(0, runner.state.value?.rounds)
    }

    @Test
    fun `each round logs on the side it turns to and counts up`() {
        start()
        hand.now = inTheCap
        repeat(3) { runner.roundDone() }
        assertEquals(listOf<Pair<Side?, Int>>(Side.Left to 1, Side.Right to 2, Side.Left to 3), logged)
        assertEquals(3, runner.state.value?.rounds)
        assertEquals(14, runner.state.value?.best)
    }

    @Test
    fun `a round tapped in the cap's last second counts and one after it does not`() {
        start()
        hand.now = DensityClock(5).lengthMillis - 500
        runner.roundDone()
        hand.now = DensityClock(5).lengthMillis + 500
        runner.roundDone()
        assertEquals(1, logged.size)
    }

    @Test
    fun `it says a cue as it counts in then only the clock and never a word at a round`() {
        start(minutes = 10)
        assertEquals(listOf<Coaching>(Coaching.Cue(snatch, 0)), voice.lines)
        hand.now = inTheCap
        runner.roundDone()
        runner.tick(hand.now)
        assertEquals(1, voice.lines.size)
        val end = DensityClock(10).lengthMillis
        hand.now = end - 5 * EmomClock.MINUTE_MILLIS
        runner.tick(hand.now)
        hand.now = end - EmomClock.MINUTE_MILLIS
        runner.tick(hand.now)
        runner.roundDone()
        hand.now = end
        runner.tick(hand.now)
        assertEquals(
            listOf(
                Coaching.Cue(snatch, 0),
                Coaching.Clock(snatch, DensityCall.FiveMinutes, 1),
                Coaching.Clock(snatch, DensityCall.OneMinute, 1),
                Coaching.Clock(snatch, DensityCall.Time, 2),
            ),
            voice.lines,
        )
    }

    @Test
    fun `it beeps the start and the finish and ends once Done has shown`() {
        start()
        hand.now = inTheCap
        runner.tick(hand.now)
        assertEquals(EmomCue.Start, beeper.cues.last())
        hand.now = DensityClock(5).lengthMillis
        runner.tick(hand.now)
        assertEquals(EmomCue.Finished, beeper.cues.last())
        hand.now += EmomClock.DONE_HOLD_MILLIS
        runner.tick(hand.now)
        assertNull(runner.state.value)
    }

    @Test
    fun `a run resumed says nothing for the time away`() {
        start()
        val run = runner.state.value ?: error("Not started")
        runner.stop()
        voice.lines.clear()
        hand.now = DensityClock(5).lengthMillis - 10_000
        runner.resume(run.copy(rounds = 6))
        runner.tick(hand.now)
        assertEquals(emptyList(), voice.lines)
        assertEquals(6, runner.state.value?.rounds)
    }
}
