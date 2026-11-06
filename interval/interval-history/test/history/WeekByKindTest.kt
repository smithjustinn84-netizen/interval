package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekByKindTest {
    private val tabata = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8)
    private val flow = IntervalConfig(workSeconds = 45, restSeconds = 0, rounds = 1, activity = Activity.Mobility)

    private fun plan(workout: PlanWorkout, state: PlanState = PlanState.Open) =
        DayPlan(PlannedWorkout(1, workout, PlanSchedule.Once(0)), state)

    private fun day(
        plans: List<DayPlan> = emptyList(),
        workouts: List<IntervalConfig> = emptyList(),
        lifts: Int = 0,
        sports: Int = 0,
    ) = TrainingDay(
        epochDay = 0,
        isToday = false,
        plans = plans,
        workouts = workouts.map { HistoryEntry(0, it, 240, endedEarly = false) },
        liftWorkouts = List(lifts) { LiftWorkout("s$it", 0, 900, "Day 1") },
        sports = List(sports) { LoggedSport(it.toLong(), Sport.Bjj, 0, 0) },
    )

    @Test
    fun `each kind counts what was done against what was planned`() {
        val week = listOf(
            day(plans = listOf(plan(PlanWorkout.Lift.ProgramDay, PlanState.Done)), lifts = 1),
            day(
                plans = listOf(plan(PlanWorkout.SportDay(Sport.Bjj), PlanState.Done)),
                workouts = listOf(flow),
                sports = 1,
            ),
            day(plans = listOf(plan(PlanWorkout.Lift.ProgramDay), plan(PlanWorkout.Interval("Tabata", tabata)))),
            day(plans = listOf(plan(PlanWorkout.SportDay(Sport.Climbing)), plan(PlanWorkout.SportDay(Sport.Bjj)))),
        )

        assertEquals(
            listOf(
                KindTally(WorkoutKind.Strength, done = 1, planned = 2),
                KindTally(WorkoutKind.Conditioning, done = 0, planned = 1),
                KindTally(WorkoutKind.Mobility, done = 1, planned = 0),
                KindTally(WorkoutKind.Sport, done = 1, planned = 3),
            ),
            week.byKind(),
        )
        assertEquals(1, week.byKind()[2].of)
    }

    @Test
    fun `a kind neither done nor planned is left out`() {
        val tallies = listOf(day(workouts = listOf(tabata, tabata))).byKind()
        assertEquals(listOf(KindTally(WorkoutKind.Conditioning, done = 2, planned = 0)), tallies)
        assertTrue(listOf(day()).byKind().isEmpty())
    }

    @Test
    fun `a flow planned is mobility`() {
        assertEquals(WorkoutKind.Mobility, PlanWorkout.Interval("Hips", flow).kind)
        assertEquals(WorkoutKind.Strength, PlanWorkout.Lift.AnySession.kind)
    }
}
