package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/** `journeys/plan-lift.xml`: plan a session on Lift, start it from Up next, and find the plan done. */
class PlanLiftAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a session on Lift planned for today is done by a session logged on Lift`() = acceptanceScenario {
        openTab("History")
        openPlanFromEmptyHistory()
        planAnyLiftSessionEvery("Thursday")
        seeLiftSessionPlannedOn("Thursday")
        goBack()

        seeUpNext("Lift session")
        startUpNext()
        logASetAndFinish()

        openTab("History")
        openToday()
        seeTodaysPlanDone("Every Thursday")
    }
}
