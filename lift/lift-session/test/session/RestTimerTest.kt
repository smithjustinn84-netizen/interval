package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingVoice
import dev.justinsmith.lift.platform.Coaching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestTimerTest {
    private val hand = HandTime(1_000L)
    private val beeper = RecordingBeeper()
    private val voice = RecordingVoice()
    private var next: Exercise? = BuiltInExercise.Clean
    private val timer = RestTimer(hand.clock, beeper, voice) { next }

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

    @Test
    fun `the last ten seconds say the set-up of the lift coming`() {
        timer.start(30)
        hand.now += 19_000
        timer.tick(hand.now)
        assertEquals(emptyList(), voice.lines)
        hand.now += 2_000
        timer.tick(hand.now)
        assertEquals(listOf<Coaching>(Coaching.Cue(BuiltInExercise.Clean, 0)), voice.lines)
    }

    @Test
    fun `nothing is said with no lift coming`() {
        next = null
        timer.start(30)
        hand.now += 30_000
        timer.tick(hand.now)
        assertEquals(emptyList(), voice.lines)
    }
}
