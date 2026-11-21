package dev.justinsmith.interval.today

import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekDatesTest {
    private fun day(state: DayState, today: Boolean = false) = DayCell(20_741, 4, 19, state, 0, today)

    @Test
    fun `a day still ahead says its date until it is done`() {
        assertEquals(true, day(DayState.Planned, today = true).dated(ahead = true))
        assertEquals(true, day(DayState.Rest, today = true).dated(ahead = true))
        assertEquals(true, day(DayState.Planned).dated(ahead = true))
        assertEquals(true, day(DayState.Rest).dated(ahead = true))
        assertEquals(false, day(DayState.Done, today = true).dated(ahead = true))
    }

    @Test
    fun `a day gone by keeps its mark rather than a date`() {
        DayState.entries.forEach { state ->
            assertEquals(false, day(state).dated(ahead = false), "$state gone by")
        }
    }
}
