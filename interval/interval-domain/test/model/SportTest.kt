package dev.justinsmith.interval.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportTest {
    @Test
    fun `a typed name is trimmed and cut to length`() {
        assertEquals(Sport.Custom("Judo"), Sport.custom("  Judo "))
        assertEquals(Sport.NAME_MAX_LENGTH, Sport.custom("x".repeat(40))?.name?.length)
        // Cut at a space, it ends on the word before.
        assertEquals("a".repeat(23), Sport.custom("a".repeat(23) + " b")?.name)
    }

    @Test
    fun `a blank name names no sport`() {
        assertNull(Sport.custom(""))
        assertNull(Sport.custom("   "))
    }

    @Test
    fun `a name is shortened to its first word and five letters at most`() {
        assertEquals("Judo", shortSportName("Judo"))
        assertEquals("Swimm", shortSportName("Swimming"))
        assertEquals("Trail", shortSportName("Trail running"))
        assertEquals("Yoga", shortSportName(" Yoga flow"))
    }
}
