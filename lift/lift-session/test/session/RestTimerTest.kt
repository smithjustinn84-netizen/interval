package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingRestAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestTimerTest {
    private var now = 1_000L
    private val beeper = RecordingBeeper()
    private val alerts = RecordingRestAlerts()
    private val timer = RestTimer({ now }, beeper, alerts)

    @Test
    fun `a rest beeps into its end and is over`() {
        timer.start(30)
        assertEquals(RestRun(1_000, 30), timer.state.value)
        now += 27_000
        timer.tick(now)
        assertEquals(listOf(EmomCue.Countdown), beeper.cues)
        now += 3_000
        timer.tick(now)
        assertEquals(listOf(EmomCue.Countdown, EmomCue.Start), beeper.cues)
        assertNull(timer.state.value)
    }

    @Test
    fun `a rest made longer runs on from when it started`() {
        timer.start(30)
        now += 20_000
        timer.tick(now)
        timer.adjust(45)
        assertEquals(RestRun(1_000, 45), timer.state.value)
        now += 20_000
        timer.tick(now)
        assertEquals(45, timer.state.value?.seconds)
    }

    @Test
    fun `a rest made shorter than it has run is over without a beep`() {
        timer.start(60)
        now += 40_000
        timer.tick(now)
        timer.adjust(30)
        timer.tick(now)
        assertNull(timer.state.value)
        assertEquals(emptyList(), beeper.cues)
    }

    @Test
    fun `the platform keeps the rest to its end and is told when it is over`() {
        timer.start(30)
        assertEquals(listOf(31_000L), alerts.ends)
        timer.adjust(45)
        assertEquals(listOf(31_000L, 46_000L), alerts.ends)
        now += 45_000
        timer.tick(now)
        assertEquals(1, alerts.stops)
        timer.skip()
        assertEquals(1, alerts.stops)
    }

    @Test
    fun `a rest skipped is over without a beep`() {
        timer.start(30)
        timer.skip()
        now += 30_000
        timer.tick(now)
        assertNull(timer.state.value)
        assertEquals(emptyList(), beeper.cues)
    }
}
