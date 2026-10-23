package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekStandingTest {
    // A Monday, so the week runs from it; the weeks before start seven days earlier each.
    private val monday = weekStartOf(20_000L)
    private val lastWeek = monday - DAYS_PER_WEEK
    private val twoWeeksAgo = lastWeek - DAYS_PER_WEEK

    @Test
    fun `the count is this week's workouts alone`() {
        val standing = weekStanding(listOf(monday, monday + 2, lastWeek + 3), today = monday + 2, goal = null)
        assertEquals(2, standing.count)
        assertEquals(0, standing.streak)
        assertFalse(standing.goalMet)
    }

    @Test
    fun `a week that meets the goal joins the weeks before it in the streak`() {
        val days = listOf(twoWeeksAgo, twoWeeksAgo + 1, lastWeek, lastWeek + 4, monday, monday + 1)
        val standing = weekStanding(days, today = monday + 1, goal = 2)
        assertEquals(3, standing.streak)
        assertTrue(standing.goalMet)
        assertTrue(standing.justMet)
    }

    @Test
    fun `a week past its goal has met it but is not the moment it did`() {
        val standing = weekStanding(listOf(monday, monday + 1, monday + 2), today = monday + 2, goal = 2)
        assertTrue(standing.goalMet)
        assertFalse(standing.justMet)
    }

    @Test
    fun `a week short of the goal breaks the streak`() {
        val days = listOf(twoWeeksAgo, twoWeeksAgo + 1, lastWeek, monday, monday + 1)
        assertEquals(1, weekStanding(days, today = monday + 1, goal = 2).streak)
    }

    @Test
    fun `this week still short counts the streak from last week`() {
        val days = listOf(lastWeek, lastWeek + 1, monday)
        val standing = weekStanding(days, today = monday, goal = 2)
        assertEquals(1, standing.streak)
        assertFalse(standing.goalMet)
    }
}
