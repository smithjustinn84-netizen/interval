package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/health-connect.xml`: connect Health Connect in Settings, finish a workout and find it there, then
 * disconnect and see the next one stay out. The permission sheet is Health Connect's own, so the JVM stands
 * the store connected as if it had been allowed.
 */
class HealthConnectAcceptanceTest {
    @Test
    fun `a connected Health Connect gets each finished workout until it is disconnected`() {
        acceptanceScenario {
            openSettings()
            seeHealthConnect(on = false)
            allowHealthConnect()
            seeHealthConnect(on = true)
            pressSystemBack()
            seeTimer()
            endWorkoutAfterFirstRound()
            seeSummarySaved()
            seeWorkoutInHealthConnect()
            closeSummary()
            seeTimer()
            openSettings()
            toggleHealthConnect()
            seeHealthConnect(on = false)
            pressSystemBack()
            seeTimer()
            endWorkoutAfterFirstRound()
            seeSummarySaved()
            seeNothingMoreInHealthConnect()
        }
    }
}
