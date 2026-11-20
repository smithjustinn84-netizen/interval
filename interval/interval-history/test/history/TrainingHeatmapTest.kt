package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingHeatmapTest {
    // Epoch day 10 is a Sunday (day 0 = Thursday, day 4 = Monday, day 10 = Sunday); day 100 = Saturday, day 97 = Wed.
    private val wednesday = 97L

    @Test
    fun `heatmapDays builds 12 full weeks Monday through Sunday ending on this week`() {
        val log = TrainingLog(
            history = emptyList(),
            planned = emptyList(),
            today = wednesday,
            dayOf = { it },
        )
        val days = heatmapDays(log, today = wednesday, weeks = 12)
        assertEquals(12 * DAYS_PER_WEEK, days.size)
        assertEquals(1, days.first().weekday)
        assertEquals(DAYS_PER_WEEK, days.last().weekday)
        assertEquals(1, days.count { it.today })
        assertEquals(wednesday, days.single { it.today }.epochDay)
        // Thursday, Friday, Saturday, Sunday after Wednesday are future.
        assertEquals(4, days.count { it.future })
    }

    @Test
    fun `heatmapDays splits strength conditioning mobility and sport per day`() {
        val conditioning = HistoryEntry(
            id = 1L,
            finishedAtEpochMillis = wednesday,
            config = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8, activity = Activity.General),
            elapsedSeconds = 240,
            endedEarly = false,
        )
        val mobility = HistoryEntry(
            id = 2L,
            finishedAtEpochMillis = wednesday,
            config = IntervalConfig(workSeconds = 60, restSeconds = 0, rounds = 5, activity = Activity.Mobility),
            elapsedSeconds = 300,
            endedEarly = false,
        )
        val lift =
            LiftWorkout(uid = "l1", title = "Day 1", finishedAtEpochMillis = wednesday - 1, elapsedSeconds = 1200)
        val sport = LoggedSport(id = 1L, epochDay = wednesday - 2, sport = Sport.Bjj, loggedAtEpochMillis = 0L)

        val log = TrainingLog(
            history = listOf(conditioning, mobility),
            planned = emptyList(),
            today = wednesday,
            lifts = listOf(lift),
            sports = listOf(sport),
            dayOf = { it },
        )
        val byDay = heatmapDays(log, today = wednesday, weeks = 2).associateBy { it.epochDay }

        val wed = byDay.getValue(wednesday)
        assertTrue(wed.active)
        assertEquals(240, wed.intervalSeconds)
        assertEquals(300, wed.mobilitySeconds)
        assertEquals(0, wed.liftSeconds)
        assertFalse(wed.sportLogged)

        val tue = byDay.getValue(wednesday - 1)
        assertEquals(1200, tue.liftSeconds)
        assertEquals(0, tue.intervalSeconds)

        val mon = byDay.getValue(wednesday - 2)
        assertTrue(mon.active)
        assertTrue(mon.sportLogged)
        assertEquals(0, mon.totalSeconds)
    }
}
