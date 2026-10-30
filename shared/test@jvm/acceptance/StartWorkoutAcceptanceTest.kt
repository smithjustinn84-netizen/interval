package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * ATDD Executable Specification: Start a workout journey.
 *
 * Directly mirrors the acceptance criteria defined in journeys/start-workout.xml:
 * "Edit the next workout's work time, start it, pause and resume, skip ahead, then end it before
 * any round is finished."
 */
class StartWorkoutAcceptanceTest {

    @Test
    fun `user can configure work time, start, pause, resume, skip, and discard workout before completion`() =
        acceptanceScenario {
            // 1. Verify that the Timer tab is shown with a "Start workout" button
            verifyTimerScreenShown()

            // 2. Tap the "Work" row under "Intervals"
            tapIntervalRow("Work")

            // 3. Verify that a sheet titled "Work" is shown with "Quick picks"
            verifyValueSheetShown("Work")

            // 4. Tap the "1:00" quick pick
            selectQuickPick("1:00")

            // 5. Tap "Done"
            tapDone()

            // 6. Verify that the "Work" row shows 1:00
            verifyIntervalValue("Work", "1:00")

            // 7. Tap "Start workout"
            startWorkout()

            // 8. Verify that the workout screen shows "Get ready" and a countdown clock
            verifyWorkoutPhase("Get ready")

            // 9. Tap "Pause"
            pauseWorkout()

            // 10. Verify that the screen shows "Paused" and a "Resume" button
            verifyWorkoutPaused()

            // 11. Tap "Resume"
            resumeWorkout()

            // 12. Verify that the screen shows a "Pause" button
            verifyWorkoutRunning()

            // 13. Tap "Skip"
            skipPhase()

            // 14. Verify that the screen shows "Work" and "Round 1 of 8"
            verifyWorkoutPhase("Work")
            verifyWorkoutStatus("Round 1 of 8")

            // 15. Tap "End"
            endWorkout()

            // 16. Verify that a dialog titled "End workout?" says nothing is saved
            verifyEndWorkoutDialog("nothing is saved")

            // 17. Tap "End workout" in the dialog
            confirmEndWorkout()

            // 18. Verify that the Timer tab is shown with a "Start workout" button
            verifyTimerScreenShown()
        }
}
