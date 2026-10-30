package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** `journeys/start-workout.xml`: edit the work time, start, pause and resume, skip ahead, end before a round. */
class StartWorkoutAcceptanceTest {
    @Test
    fun `a workout ended before its first round saves nothing`() = acceptanceScenario {
        seeTimer()
        setWorkTime("1:00")

        startWorkout()
        seePhase("Get ready")
        pause()
        seePaused()
        resume()
        seeRunning()

        skipPhase()
        seePhase("Work")
        seeRound("Round 1 of 8")

        askToEnd()
        seeEndDialogSaying("nothing is saved")
        confirmEnd()

        seeTimer()
        nothingIsSaved()
    }

    @Test
    fun `a workout left to run to its end is saved whole`() = acceptanceScenario {
        startWorkout()
        seePhase("Get ready")
        // 10 s to get ready, then eight rounds of 30 s on and seven rests of 15 s between them.
        letTimePass(9.seconds)
        seePhase("Get ready")
        letTimePass(2.seconds)
        seePhase("Work")
        seeRound("Round 1 of 8")
        letTimePass((10 + 8 * 30 + 7 * 15).seconds)
        see("Saved to history")
        savedWorkouts(1)
    }
}
