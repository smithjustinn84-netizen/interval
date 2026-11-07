package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/** `journeys/history.xml`: plan from the first run, end after a round, find it in Log, then clear it. */
class HistoryAcceptanceTest {
    @Test
    fun `a workout ended after a round is kept in history until it is cleared`() = acceptanceScenario {
        openTab("Log")
        seeLogEmpty()
        openPlanFromEmptyHistory()
        raiseWeeklyGoal()
        seeWeeklyGoal(3)
        goBack()

        openTimer()
        endWorkoutAfterFirstRound()
        seeSummarySaved()
        savedWorkouts(1)
        closeSummary()
        leaveTimer()

        openTab("Log")
        seeThisWeek(done = 1, goal = 3)
        openWorkoutUnderToday("30s on · 15s off · ×8")
        seeEndedEarly()
        goBack()

        showMonth()
        seeOneDayDoneOnMonth()
        clearHistory()
        seeLogEmpty()
        nothingIsSaved()
    }

    @Test
    fun `the week's bars count each kind against its plan`() = acceptanceScenario {
        openTab("Log")
        openPlanFromEmptyHistory()
        planSportEvery("BJJ", weekday = "Thursday")
        planSportEvery("BJJ", weekday = "Saturday")
        goBack()
        seeKindBar("Sport days", done = 0, of = 2)
        seeComingUp("BJJ class")
        // Today's row is logged from here; Saturday's is only named, under its day.
        seeComingUpOn("Today", "BJJ class")
        seeComingUpOn("Sat", "BJJ class")

        openTab("Today")
        logClassFromToday()
        sportDaysLogged(1)
        openTab("Log")
        seeKindBar("Sport days", done = 1, of = 2)
    }
}
