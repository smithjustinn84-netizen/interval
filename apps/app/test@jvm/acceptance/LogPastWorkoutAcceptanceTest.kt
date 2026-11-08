package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/log-past-workout.xml`: a workout done away from the app, or without the Timer, goes into the Log after
 * the fact from its day's sheet, today or a day gone by: a preset, a flow or a sport day, in full, with Undo; a
 * workout planned that day is logged from its row.
 */
class LogPastWorkoutAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970: yesterday is Wednesday, epoch day -1. The week shows
    // once anything is planned or done, so each starts by planning the Timer's workout for today.
    @Test
    fun `a workout done yesterday is logged on its day and undone from the notice`() = acceptanceScenario {
        openTab("Log")
        openPlanFromEmptyHistory()
        planTimerWorkoutEvery("Thursday")
        goBack()

        openDayOnTheWeek("Wednesday, no workout")
        logAWorkoutOnItsDay("Tabata")
        see("Tabata logged")
        workoutLoggedOn(-1, "Tabata")
        seeDayOnTheWeek("Wednesday, done")
        // The week's log heads a day by its weekday.
        seeLoggedUnder("Wed", "Tabata")

        undoNotice()
        workoutsLogged(0)
        seeDayOnTheWeek("Wednesday, no workout")
    }

    @Test
    fun `a flow done without the app is logged on its day and counts as mobility`() = acceptanceScenario {
        openTab("Log")
        openPlanFromEmptyHistory()
        planTimerWorkoutEvery("Thursday")
        goBack()

        openDayOnTheWeek("Wednesday, no workout")
        logAFlowOnItsDay("Hips and shoulders")
        see("Hips and shoulders logged")
        workoutLoggedOn(-1, "Hips and shoulders")
        seeFlowInHistory("Hips and shoulders", "6 moves")
        seeKindBar("Mobility", done = 1, of = 1)
    }

    @Test
    fun `today's planned workout is logged from its row without the Timer`() = acceptanceScenario {
        openTab("Log")
        openPlanFromEmptyHistory()
        planTimerWorkoutEvery("Thursday")
        goBack()

        openDayOnTheWeek("Today, planned")
        logPlanFromItsDay()
        seePlanDoneOnItsDay("Every Thursday")
        workoutLoggedOn(0)
        dismissSheet()

        openTab("Today")
        seeNothingMoreDue()
    }
}
