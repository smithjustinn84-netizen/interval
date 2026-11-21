package dev.justinsmith.interval.history

import dev.justinsmith.interval.timer.weekdayOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The followed program's day with no plan of it: when it falls, by the pace its days a week set. */
class ProgramPaceTest {
    // Thursday 1 January 1970; the Monday of its week is day -3, the next Monday day 4.
    private val thursday = 0L
    private val monday = 4L

    @Test
    fun `the epoch's first day is a thursday`() {
        assertEquals(4, weekdayOf(thursday))
        assertEquals(1, weekdayOf(monday))
    }

    @Test
    fun `with no session of it the program's day is today`() {
        assertEquals(thursday, programDayOn(daysPerWeek = 3, sessionDays = emptyList(), today = thursday))
    }

    @Test
    fun `three days a week leave a day of rest after each session`() {
        assertEquals(thursday + 2, programDayOn(3, listOf(thursday), today = thursday))
        assertEquals(thursday + 1, programDayOn(3, listOf(thursday - 1), today = thursday))
    }

    @Test
    fun `two days a week leave two days of rest after each session`() {
        assertEquals(thursday + 3, programDayOn(2, listOf(thursday), today = thursday))
    }

    @Test
    fun `five days a week come every day`() {
        assertEquals(thursday + 1, programDayOn(5, listOf(thursday), today = thursday))
    }

    @Test
    fun `a day put off from today falls tomorrow and keeps the pace after it`() {
        assertEquals(thursday + 1, programDayOn(3, emptyList(), today = thursday, putOff = thursday))
        // Put off yesterday: today is the day again.
        assertEquals(thursday, programDayOn(3, emptyList(), today = thursday, putOff = thursday - 1))
    }

    @Test
    fun `a session long gone by leaves the day today`() {
        assertEquals(thursday, programDayOn(3, listOf(thursday - 10), today = thursday))
    }

    @Test
    fun `a week that holds its sessions already waits for the monday after`() {
        // Monday, Wednesday and Friday of a three-day program: the weekend is rest.
        val week = listOf(monday, monday + 2, monday + 4)
        assertEquals(monday + 7, programDayOn(3, week, today = monday + 5))
        // Five days of a five-day program, Monday to Friday.
        assertEquals(monday + 7, programDayOn(5, (monday..monday + 4).toList(), today = monday + 4))
    }
}
