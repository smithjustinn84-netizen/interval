package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarDaysTest {
    @Test
    fun `the epoch is day 0 and a Thursday`() {
        assertEquals(0L, epochDayOf(1970, 1, 1))
        assertEquals(CalendarDate(1970, 1, 1), dateOfEpochDay(0))
        assertEquals(4, weekdayOf(0))
    }

    @Test
    fun `dates round trip through epoch days across leap years and before the epoch`() {
        val days = (-800_000L..800_000L step 997) + listOf(-1L, 59L, 60L, 11_016L, 20_744L)
        days.forEach { day -> assertEquals(day, dateOfEpochDay(day).epochDay) }
        // 29 February 2000 and 2024 exist; 1900 had none.
        assertEquals(CalendarDate(2000, 2, 29), dateOfEpochDay(epochDayOf(2000, 2, 29)))
        assertEquals(epochDayOf(2024, 3, 1) - 1, epochDayOf(2024, 2, 29))
        assertEquals(epochDayOf(1900, 3, 1) - 1, epochDayOf(1900, 2, 28))
        assertEquals(CalendarDate(1969, 12, 31), dateOfEpochDay(-1))
    }

    @Test
    fun `weeks start on Monday`() {
        // Sunday 18 October 2026 is in the week of Monday the 12th.
        val sunday = epochDayOf(2026, 10, 18)
        assertEquals(7, weekdayOf(sunday))
        assertEquals(epochDayOf(2026, 10, 12), weekStartOf(sunday))
        assertEquals(weekStartOf(sunday), weekStartOf(weekStartOf(sunday)))
        assertEquals(epochDayOf(1969, 12, 29), weekStartOf(0))
    }

    @Test
    fun `months have their lengths and step across years`() {
        assertEquals(31, daysInMonth(2026, 10))
        assertEquals(30, daysInMonth(2026, 11))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(31, daysInMonth(2026, 12))
        val mid = epochDayOf(2026, 10, 18)
        assertEquals(epochDayOf(2026, 10, 1), monthStartOf(mid))
        assertEquals(epochDayOf(2027, 1, 1), monthStartAfter(mid, 3))
        assertEquals(epochDayOf(2025, 12, 1), monthStartAfter(mid, -10))
        assertEquals(epochDayOf(2026, 10, 1), monthStartAfter(mid, 0))
    }

    @Test
    fun `a month's days run from its first to its last`() {
        assertEquals(epochDayOf(2026, 10, 1)..epochDayOf(2026, 10, 31), monthDaysOf(epochDayOf(2026, 10, 18)))
        assertEquals(epochDayOf(2024, 2, 1)..epochDayOf(2024, 2, 29), monthDaysOf(epochDayOf(2024, 2, 29)))
        assertEquals(epochDayOf(2026, 12, 1)..epochDayOf(2026, 12, 31), monthDaysOf(epochDayOf(2026, 12, 1)))
    }
}
