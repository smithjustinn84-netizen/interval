package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test
    fun `formats clocks and short durations`() {
        assertEquals("0:05", formatClock(5))
        assertEquals("5:55", formatClock(355))
        assertEquals(ShortDuration.Minutes(1), shortDuration(60))
        assertEquals(ShortDuration.Seconds(45), shortDuration(45))
        assertEquals(ShortDuration.Seconds(90), shortDuration(90))
        assertEquals(1, ceilSeconds(1))
        assertEquals(0, ceilSeconds(0))
    }

    @Test
    fun `a day's time reads in seconds under a minute and whole minutes from one`() {
        assertEquals(SpokenDuration.Seconds(1), spokenDuration(1))
        assertEquals(SpokenDuration.Seconds(45), spokenDuration(45))
        assertEquals(SpokenDuration.Minutes(1), spokenDuration(60))
        // 90 seconds rounds to 2 minutes.
        assertEquals(SpokenDuration.Minutes(2), spokenDuration(90))
        assertEquals(SpokenDuration.Minutes(4), spokenDuration(240))
    }
}
