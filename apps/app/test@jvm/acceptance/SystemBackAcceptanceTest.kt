package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/** `journeys/system-back.xml`: system back returns to Today, and during a workout pauses and asks first. */
class SystemBackAcceptanceTest {
    @Test
    fun `system back returns to today from settings and a tab`() = acceptanceScenario {
        tapDescribed("Settings")
        see("Sound cues")
        pressSystemBack()
        seeTodayTab()

        openTab("History")
        see("Train to a plan")
        pressSystemBack()
        seeTodayTab()

        openTab("Timer")
        seeTimer()
        pressSystemBack()
        seeTodayTab()
    }

    @Test
    fun `system back during a workout pauses and asks before ending`() = acceptanceScenario {
        openTab("Timer")
        startWorkout()
        seePhase("Get ready")

        pressSystemBack()
        seeEndDialogSaying("nothing is saved")
        seePaused()
        resumeFromDialog()
        seeRunning()

        pressSystemBack()
        confirmEnd()
        seeTimer()
        nothingIsSaved()
    }
}
