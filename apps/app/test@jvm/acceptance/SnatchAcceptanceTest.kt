package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * `journeys/lift-challenges.xml`'s 15:15 step: the snatches of 15:15 MVO2, counted on its summary, show on Lift's
 * side as if logged there: the snatch test's best five minutes, named by the preset, and the lift's reps in all.
 */
class SnatchAcceptanceTest {
    @Test
    fun `15-15's snatches counted on its summary move Lift's snatch test and total`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Conditioning")
        seeConditioning()
        openPreset("15:15 MVO2")
        startWorkout()
        seePhase("Get ready")
        letTimePass(21.minutes)
        seeAskedForSnatches()
        countSnatches(perSet = 8, kg = 16)
        workoutCountedAs(perSet = 8, kg = 16.0)
        closeSummary()

        openTab("Train")
        pickPillar("Strength")
        seeSnatchTestBest(80, from = "15:15 MVO2")
        openSnatchPage()
        seeRepsInAll(320)
    }

    @Test
    fun `a workout with no snatches in it asks for none`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Conditioning")
        openPreset("Tabata")
        startWorkout()
        seePhase("Get ready")
        letTimePass(5.minutes)
        seeSummaryFinished()
        seeNotAskedForSnatches()
    }
}
