package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekdaysTest {
    private val week = (1..7).toSet()

    @Test
    fun `days spread round the week as evenly as they go`() {
        assertEquals(setOf(1), spreadWeekdays(week, 1))
        assertEquals(setOf(1, 4), spreadWeekdays(week, 2))
        assertEquals(setOf(1, 3, 5), spreadWeekdays(week, 3))
        assertEquals(setOf(1, 2, 4, 6), spreadWeekdays(week, 4))
        assertEquals(setOf(2, 4, 6), spreadWeekdays(week - setOf(1, 3, 5), 3))
    }

    @Test
    fun `only free days are taken and never more than there are`() {
        assertEquals(setOf(6, 7), spreadWeekdays(setOf(6, 7), 5))
        assertTrue(spreadWeekdays(week, 0).isEmpty())
        assertTrue(spreadWeekdays(emptySet(), 2).isEmpty())
    }

    @Test
    fun `a day included is always among them and the spread counts on from it`() {
        assertEquals(setOf(1, 3, 5), spreadWeekdays(week, 3, including = 1))
        assertEquals(setOf(3, 5, 7), spreadWeekdays(week, 3, including = 3))
        assertEquals(setOf(1, 4), spreadWeekdays(week, 2, including = 1))
        assertEquals(setOf(6, 7, 1, 2, 4), spreadWeekdays(week, 5, including = 6))
    }

    @Test
    fun `a count out of the week's range is kept within it`() {
        assertEquals(week, spreadWeekdays(week, 9, including = 2))
        assertEquals(setOf(2), spreadWeekdays(week, 0, including = 2))
    }
}
