package dev.justinsmith.lift.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-density.xml`: a density block, as many rounds of one lift as fit a time cap. Made in the program
 * editor, it starts from the session's foot, counts in, takes a tap a round with the rounds rolling up, and calls
 * the clock (one minute left, then time and the rounds) without a word mid-round. The summary says the rounds, and
 * the next session of it, its best to beat on the clock, says the new best it set.
 */
class LiftDensityAcceptanceTest {
    @Test
    fun `a density block runs against its cap and the next session beats its best`() = liftScenario {
        followNewDensityProgram("Density")
        tap("Start day 1")
        startTheClock()
        roundsDone(3, total = 3)
        secondsPass(240)
        hear("One minute")
        secondsPass(60)
        hear("Time. 3 rounds")
        see("Done")
        secondsPass(3)
        see("Extra set")
        finish()
        see("3 rounds in 5 min")
        closeSummary()

        tap("Start day 2")
        startTheClock()
        see("Best: 3")
        roundsDone(4, total = 4)
        secondsPass(300)
        hear("Time. 4 rounds")
        secondsPass(3)
        finish()
        see("New best: 4 rounds in 5 min")
    }
}
