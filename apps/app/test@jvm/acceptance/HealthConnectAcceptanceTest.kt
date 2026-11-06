package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/health-connect.xml`: connect Health Connect in Settings, take the weight it holds, finish a workout
 * and find it there, then disconnect and see the next one stay out; and a watch's heart rate on a finished
 * workout's summary and page. The permission sheet is Health Connect's own, so the JVM stands the store connected
 * as if it had been allowed, and there is no watch: the store is handed the heart rate it recorded.
 */
class HealthConnectAcceptanceTest {
    @Test
    fun `a connected Health Connect gives its weight and gets each finished workout until it is disconnected`() {
        acceptanceScenario {
            openTimer()
            openSettings()
            seeHealthConnect(on = false)
            allowHealthConnect()
            seeHealthConnect(on = true)
            weighInHealthConnect(82.0)
            pressSystemBack()
            seeTimer()
            openSettings()
            takeHealthConnectWeight("82 kg")
            seeBodyWeight(82)
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

    @Test
    fun `a watch's heart rate shows on the summary when it has synced and on the workout's page when it syncs later`() {
        acceptanceScenario {
            openTimer()
            allowHealthConnect()
            watchSyncsHeartRate(average = 142, peak = 171)
            endWorkoutAfterFirstRound()
            seeSummarySaved()
            seeSummaryHeartRate(average = 142, peak = 171)
            closeSummary()
            leaveTimer()
            openTab("Log")
            openWorkoutUnderToday("30s on · 15s off · ×8")
            seeWorkoutHeartRate(average = 142, peak = 171)
        }
    }

    @Test
    fun `a watch that syncs after the summary closed shows its heart rate on the workout's page`() {
        acceptanceScenario {
            openTimer()
            allowHealthConnect()
            endWorkoutAfterFirstRound()
            seeSummarySaved()
            seeNoSummaryHeartRate()
            closeSummary()
            watchSyncsHeartRate(average = 138, peak = 165)
            leaveTimer()
            openTab("Log")
            openWorkoutUnderToday("30s on · 15s off · ×8")
            seeWorkoutHeartRate(average = 138, peak = 165)
        }
    }
}
