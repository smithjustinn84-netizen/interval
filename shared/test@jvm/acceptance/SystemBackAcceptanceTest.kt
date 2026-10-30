package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/** `journeys/system-back.xml`: system back returns to the Timer, and during a workout pauses and asks first. */
class SystemBackAcceptanceTest {
    @Test
    fun `system back returns to the timer from settings and a tab`() = acceptanceScenario {
        tapDescribed("Settings")
        see("Sound cues")
        pressSystemBack()
        seeTimer()

        openTab("History")
        see("Train to a plan")
        pressSystemBack()
        seeTimer()
    }

    @Test
    fun `system back during a workout pauses and asks before ending`() = acceptanceScenario {
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
