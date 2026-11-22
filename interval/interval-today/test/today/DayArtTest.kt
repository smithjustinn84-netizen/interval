package dev.justinsmith.interval.today

import dev.justinsmith.interval.model.Sport
import kotlin.test.Test
import kotlin.test.assertEquals

class DayArtTest {
    @Test
    fun `every day kind maps to its matching editorial plate`() {
        assertEquals(DayArt.Strength, DayKind.Strength.art)
        assertEquals(DayArt.Conditioning, DayKind.Conditioning.art)
        assertEquals(DayArt.Mobility, DayKind.Mobility.art)
        assertEquals(DayArt.Rest, DayKind.Rest.art)
        assertEquals(DayArt.Done, DayKind.Done.art)
    }

    @Test
    fun `built-in sports take their own plate and custom sports take the general sport plate`() {
        assertEquals(DayArt.Bjj, DayKind.SportDay(Sport.Bjj).art)
        assertEquals(DayArt.Climbing, DayKind.SportDay(Sport.Climbing).art)
        assertEquals(DayArt.Running, DayKind.SportDay(Sport.Running).art)
        assertEquals(DayArt.Sport, DayKind.SportDay(Sport.Custom("Judo")).art)
    }
}
