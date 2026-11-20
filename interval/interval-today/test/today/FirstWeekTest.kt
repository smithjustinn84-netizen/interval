package dev.justinsmith.interval.today

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirstWeekTest {
    private val climbing = PlannedWorkout(1, PlanWorkout.SportDay(Sport.Climbing), PlanSchedule.Weekly(4))
    private val workout = HistoryEntry(0, IntervalConfig(), elapsedSeconds = 240, endedEarly = false)
    private val flow = workout.copy(config = IntervalConfig(activity = Activity.Mobility))
    private val climbed = LoggedSport(1, Sport.Climbing, epochDay = 0, loggedAtEpochMillis = 0)

    private fun week(
        planned: List<PlannedWorkout> = emptyList(),
        following: Boolean = false,
        history: List<HistoryEntry> = emptyList(),
        lifted: Boolean = false,
        sports: List<LoggedSport> = emptyList(),
    ) = firstWeek(planned, following, history, lifted, sports)

    private fun FirstWeek.done(kind: FirstWeekStep.Kind) = steps.single { it.kind == kind }.done

    @Test
    fun `a new install has three things to do and none done`() {
        val week = week()
        assertEquals(
            listOf(FirstWeekStep.Kind.Build, FirstWeekStep.Kind.Workout, FirstWeekStep.Kind.Flow),
            week.steps.map { it.kind },
        )
        assertEquals(0, week.done)
        assertFalse(week.finished)
    }

    @Test
    fun `a plan or a program followed is the week built`() {
        assertTrue(week(planned = listOf(climbing)).done(FirstWeekStep.Kind.Build))
        assertTrue(week(following = true).done(FirstWeekStep.Kind.Build))
    }

    @Test
    fun `a sport planned adds its day to log`() {
        val step = week(planned = listOf(climbing)).steps.last()
        assertEquals(FirstWeekStep.Kind.Sport, step.kind)
        assertEquals(Sport.Climbing, step.sport)
        assertFalse(step.done)
        assertTrue(week(planned = listOf(climbing), sports = listOf(climbed)).done(FirstWeekStep.Kind.Sport))
    }

    @Test
    fun `a sport logged with none planned is asked and done`() {
        val step = week(sports = listOf(climbed)).steps.last()
        assertEquals(Sport.Climbing, step.sport)
        assertTrue(step.done)
    }

    @Test
    fun `no sport is asked of someone with none`() {
        assertNull(week(history = listOf(workout)).steps.firstOrNull { it.kind == FirstWeekStep.Kind.Sport })
    }

    @Test
    fun `an interval workout or a session on lift is the first workout`() {
        assertTrue(week(history = listOf(workout)).done(FirstWeekStep.Kind.Workout))
        assertTrue(week(lifted = true).done(FirstWeekStep.Kind.Workout))
    }

    @Test
    fun `a flow is a flow followed and not the first workout`() {
        val week = week(history = listOf(flow))
        assertTrue(week.done(FirstWeekStep.Kind.Flow))
        assertFalse(week.done(FirstWeekStep.Kind.Workout))
    }

    @Test
    fun `every step done finishes the week`() {
        val week = week(planned = listOf(climbing), history = listOf(workout, flow), sports = listOf(climbed))
        assertEquals(4, week.done)
        assertTrue(week.finished)
    }
}
