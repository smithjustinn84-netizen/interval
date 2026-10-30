package dev.justinsmith.interval.plan

import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Thursday 15 October 2026.

class RemindersTest {
    private val tabata = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)
    private val hiit = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 30, rounds = 10)
    private var nextId = 1L

    private fun weekly(weekday: Int, name: String = "Tabata", config: IntervalConfig = tabata) =
        PlannedWorkout(nextId++, name, config, PlanSchedule.Weekly(weekday))

    private fun once(day: Long, name: String = "HIIT", config: IntervalConfig = hiit) =
        PlannedWorkout(nextId++, name, config, PlanSchedule.Once(day))

    private fun workout(day: Long, config: IntervalConfig = tabata) =
        HistoryEntry(day * DAY_MILLIS + NINE_HOURS, config, elapsedSeconds = 240, endedEarly = false, id = nextId++)

    private fun reminders(
        planned: List<PlannedWorkout>,
        history: List<HistoryEntry> = emptyList(),
        now: Int = NOON,
        at: Int = EVENING,
    ) = plannedReminders(TrainingLog(history, planned, TODAY) { it.floorDiv(DAY_MILLIS) }, at, TODAY, now)

    @Test
    fun `each day with a workout to do gets one reminder at the time set`() {
        val thursdays = weekly(4)
        val sunday = once(TODAY + 3)
        val reminders = reminders(listOf(thursdays, sunday))
        assertEquals(listOf(TODAY, TODAY + 3, TODAY + 7), reminders.map { it.epochDay })
        assertTrue(reminders.all { it.minuteOfDay == EVENING })
        assertEquals(listOf(listOf(thursdays), listOf(sunday), listOf(thursdays)), reminders.map { it.plans })
    }

    @Test
    fun `today's reminder goes once its time has come`() {
        val plans = listOf(once(TODAY))
        assertEquals(listOf(TODAY), reminders(plans, now = EVENING - 1).map { it.epochDay })
        assertTrue(reminders(plans, now = EVENING).isEmpty())
    }

    @Test
    fun `a workout of the plan's shape today takes today's reminder off`() {
        val reminders = reminders(listOf(weekly(4), weekly(5)), history = listOf(workout(TODAY)))
        assertEquals(listOf(TODAY + 1, TODAY + 7, TODAY + 8), reminders.map { it.epochDay })
    }

    @Test
    fun `two workouts on one day share a reminder in the order planned`() {
        val first = weekly(4)
        val second = once(TODAY)
        val reminders = reminders(listOf(second, first).sortedBy { it.id })
        assertEquals(listOf(TODAY, TODAY + 7), reminders.map { it.epochDay })
        assertEquals(listOf(first, second), reminders.first().plans)
    }

    @Test
    fun `reminders reach two weeks ahead and never back`() {
        val reminders = reminders(listOf(once(TODAY - 1), once(TODAY + 13), once(TODAY + 14)))
        assertEquals(listOf(TODAY + 13), reminders.map { it.epochDay })
    }

    private companion object {
        const val NINE_HOURS = 9L * 60 * 60 * 1000
        const val NOON = 12 * 60
        const val EVENING = 18 * 60
    }
}
