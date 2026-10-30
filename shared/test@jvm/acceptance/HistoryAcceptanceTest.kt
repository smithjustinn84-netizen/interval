package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/** `journeys/history.xml`: plan from the first run, end after a round, find it in History, then clear it. */
class HistoryAcceptanceTest {
    @Test
    fun `a workout ended after a round is kept in history until it is cleared`() = acceptanceScenario {
        openTab("History")
        seeHistoryEmpty()
        openPlanFromEmptyHistory()
        raiseWeeklyGoal()
        seeWeeklyGoal(3)
        goBack()

        openTab("Timer")
        endWorkoutAfterFirstRound()
        seeSummarySaved()
        savedWorkouts(1)
        closeSummary()

        openTab("History")
        seeThisWeek(done = 1, goal = 3)
        openWorkoutUnderToday("30s on · 15s off · ×8")
        seeEndedEarly()
        goBack()

        showMonth()
        seeOneDayDoneOnMonth()
        clearHistory()
        seeHistoryEmpty()
        nothingIsSaved()
    }
}
