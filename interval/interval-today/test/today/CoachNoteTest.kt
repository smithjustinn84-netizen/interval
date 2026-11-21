package dev.justinsmith.interval.today

import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the coach says of a plain training day: never nothing, so the day's cards keep their place. */
class CoachNoteTest {
    private fun planned(id: Long) = UpNextItem.Planned(id, 0, HistoryDay.Today, "Tabata", IntervalConfig())

    private val twoSteps = TodayPlan(steps = listOf(planned(1), planned(2)))

    @Test
    fun `a training day short of the goal says where today takes the week`() {
        assertEquals(CoachNote.TowardGoal(days = 3, goal = 4), twoSteps.note(taxed = null, trained = 2, goal = 4))
        assertEquals(CoachNote.TowardGoal(days = 4, goal = 4), twoSteps.note(taxed = null, trained = 3, goal = 4))
    }

    @Test
    fun `a training day with the goal met or none says how much the day holds`() {
        assertEquals(CoachNote.Steps(count = 2, started = false), twoSteps.note(taxed = null, trained = 4, goal = 4))
        assertEquals(CoachNote.Steps(count = 2, started = false), twoSteps.note(taxed = null))
    }

    @Test
    fun `a training day under way says how much is left`() {
        val started = twoSteps.copy(done = listOf(DoneStep.Workout("Daily five", IntervalConfig(), 300)))
        // Today counts already, so it takes the week nowhere new.
        assertEquals(CoachNote.Steps(count = 2, started = true), started.note(taxed = null, trained = 2, goal = 4))
    }

    @Test
    fun `a day done says whether the goal is met by the days trained`() {
        val done = TodayPlan(done = listOf(DoneStep.Workout("Tabata", IntervalConfig(), 240)))
        assertEquals(CoachNote.DayDone(goalMet = true), done.note(taxed = null, trained = 4, goal = 4))
        assertEquals(CoachNote.DayDone(goalMet = false), done.note(taxed = null, trained = 3, goal = 4))
    }
}
