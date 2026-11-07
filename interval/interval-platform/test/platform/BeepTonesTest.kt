package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class BeepTonesTest {
    @Test
    fun `each beep is a tone of its own`() {
        assertEquals(Beep.entries.size, Beep.entries.map { it.tone }.toSet().size)
        assertEquals(ToneSynth.all.toSet(), Beep.entries.map { it.tone }.toSet())
    }

    @Test
    fun `work rises into the interval and rest falls out of it`() {
        assertEquals(ToneSynth.work, Beep.Work.tone)
        assertEquals(ToneSynth.rest, Beep.Rest.tone)
        assertEquals(ToneSynth.countdown, Beep.Countdown.tone)
    }
}
