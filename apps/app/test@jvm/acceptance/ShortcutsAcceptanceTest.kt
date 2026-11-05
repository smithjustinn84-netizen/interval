package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.nav.Shortcut
import kotlin.test.Test

/** `journeys/shortcuts.xml`: the launcher's shortcuts start a workout or a Lift session over their tabs. */
class ShortcutsAcceptanceTest {
    @Test
    fun `Start workout starts the Timer's workout and Back ends it to the Timer`() = acceptanceScenario {
        launchFrom(Shortcut.StartWorkout)
        seePhase("Get ready")

        pressSystemBack()
        seeEndDialogSaying("nothing is saved")
        confirmEnd()
        seeTimer()
    }

    @Test
    fun `Lift session starts a session and Back leaves it to the Lift tab`() = acceptanceScenario {
        launchFrom(Shortcut.LiftSession)
        see("Log set")
        aLiftSessionIsInProgress()

        pressSystemBack()
        leaveSession()
        seeLiftTab()
    }

    @Test
    fun `a shortcut while a workout runs returns to that workout`() = acceptanceScenario {
        openTimer()
        startWorkout()
        skipPhase()
        seePhase("Work")

        launchFrom(Shortcut.LiftSession)
        seePhase("Work")
        seeRunning()
        nothingIsSaved()
    }
}
