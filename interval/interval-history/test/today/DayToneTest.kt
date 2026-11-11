package dev.justinsmith.interval.today

import dev.justinsmith.interval.model.Sport
import kotlin.test.Test
import kotlin.test.assertEquals

class DayToneTest {
    @Test
    fun `strength a sport's day and a day done go by the accent`() {
        assertEquals(DayTone.Accent, DayKind.Strength.tone)
        assertEquals(DayTone.Accent, DayKind.SportDay(Sport.Bjj).tone)
        assertEquals(DayTone.Accent, DayKind.Done.tone)
    }

    @Test
    fun `conditioning goes by the work mark`() {
        assertEquals(DayTone.Work, DayKind.Conditioning.tone)
    }

    @Test
    fun `mobility and a rest day go by the rest mark`() {
        assertEquals(DayTone.Rest, DayKind.Mobility.tone)
        assertEquals(DayTone.Rest, DayKind.Rest.tone)
    }
}
