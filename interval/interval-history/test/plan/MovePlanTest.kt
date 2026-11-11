package dev.justinsmith.interval.plan

import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.epochDayOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovePlanTest {
    // Thursday 15 October 2026.
    private val today = epochDayOf(2026, 10, 15)
    private val tabata = PlanWorkout.Interval("Tabata", IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8))

    private fun log(planned: List<PlannedWorkout>) = TrainingLog(emptyList(), planned, today) {
        it.floorDiv(DAY_MILLIS)
    }

    @Test
    fun `a day gone by moves to a day from today on and suggests the first free one`() {
        val missed = PlannedWorkout(1, tabata, PlanSchedule.Once(today - 2))
        val friday = PlannedWorkout(2, PlanWorkout.Lift.ProgramDay, PlanSchedule.Once(today + 1))
        val bjj = PlannedWorkout(3, PlanWorkout.SportDay(Sport.Bjj), PlanSchedule.Weekly(6))
        val todays = PlannedWorkout(4, tabata, PlanSchedule.Once(today))
        val sheet = moveSheet(missed, today - 2, today, log(listOf(missed, friday, bjj, todays)))
        assertEquals((today until today + MOVE_DAYS).toList(), sheet.days.map { it.epochDay })
        assertEquals(listOf(PlanWorkout.Lift.ProgramDay), sheet.days[1].workouts)
        assertEquals(Sport.Bjj, sheet.days[2].sport)
        assertTrue(sheet.days[2].workouts.isEmpty())
        assertEquals(today + 3, sheet.suggested)
    }

    @Test
    fun `today or a day to come moves only later and never back to the day it first left`() {
        val moved = PlannedWorkout(1, tabata, PlanSchedule.Once(today, movedFromDay = today - 1))
        val sheet = moveSheet(moved, today, today, log(listOf(moved)))
        assertEquals(today + 1, sheet.days.first().epochDay)
        assertEquals(today + 1, sheet.suggested)

        val ahead = PlannedWorkout(2, tabata, PlanSchedule.Once(today + 3, movedFromDay = today + 2))
        val later = moveSheet(ahead, today + 3, today, log(listOf(ahead))).days.map { it.epochDay }
        assertEquals(today + 4, later.first())
        assertTrue(later.none { it == today + 2 || it <= today + 3 })
    }

    @Test
    fun `with no free day the first is suggested`() {
        val daily = (1..7).map { PlannedWorkout(it.toLong(), PlanWorkout.Lift.AnySession, PlanSchedule.Weekly(it)) }
        val missed = PlannedWorkout(9, tabata, PlanSchedule.Once(today - 1))
        assertEquals(today, moveSheet(missed, today - 1, today, log(daily + missed)).suggested)
    }
}
