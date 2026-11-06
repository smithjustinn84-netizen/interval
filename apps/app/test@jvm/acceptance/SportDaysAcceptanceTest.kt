package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/sport-days.xml`: a class or a day on the wall, trained away from the app, goes on the plan and shows
 * on the week beside the workouts done here.
 */
class SportDaysAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a class planned for today shows under its day on the week`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        seeSportPlannedOn("BJJ class", weekday = "Thursday")
        goBack()

        seeTodayTab()
        seeSportOnTheWeek("Today, BJJ planned")
        seeUpNext("BJJ class")
    }
}
