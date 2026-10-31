package dev.justinsmith.interval.plan

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanningTest {
    @Test
    fun `a program's days spread over the week from the day picked`() {
        assertEquals(setOf(1, 3, 5), spreadWeekdays(first = 1, count = 3))
        assertEquals(setOf(3, 5, 7), spreadWeekdays(first = 3, count = 3))
        assertEquals(setOf(1, 4), spreadWeekdays(first = 1, count = 2))
        assertEquals(setOf(6, 7, 1, 3, 4), spreadWeekdays(first = 6, count = 5))
    }

    @Test
    fun `a count out of the week's range is kept within it`() {
        assertEquals((1..7).toSet(), spreadWeekdays(first = 2, count = 9))
        assertEquals(setOf(2), spreadWeekdays(first = 2, count = 0))
    }
}
