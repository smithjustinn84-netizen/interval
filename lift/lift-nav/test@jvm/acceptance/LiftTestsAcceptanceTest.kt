package dev.justinsmith.lift.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-tests.xml`: a test, a benchmark done on purpose. Tests lists each with its last result; a test's
 * sheet says how it is done, takes the bell and shows the attempts with it; Start opens a session planned as the
 * test, on the clock a density block runs on. The reps of a round cut short by the cap are added to the count, the
 * summary says what the attempt came to, and the next at that bell says how it compares.
 */
class LiftTestsAcceptanceTest {
    @Test
    fun `a snatch test is started timed and compared with the last at its bell`() = liftScenario {
        openTests()
        see("Not tested yet")
        openTest("Snatch test")
        see("None with this bell yet: the first sets the mark.")
        see("Pass mark: 100 reps")
        choose("24 kg")
        startTheTest()
        startTheClock()
        roundsDone(9, total = 9)
        secondsPass(300)
        hear("Time. 9 rounds")
        secondsPass(3)
        addPartRound(7)
        finish()
        see("Snatch test: 97 reps with 24 kg, the mark to beat")
        closeSummary()

        openTests()
        see("97 reps with 24 kg · today")
        openTest("Snatch test")
        see("Tested today. Another now measures how tired you are: give it a few weeks.")
        see("Best 97 reps · last 97 reps")
        startTheTest()
        startTheClock()
        see("Best: 9")
        roundsDone(10, total = 10)
        secondsPass(303)
        finish()
        see("Snatch test passed: 100 reps with 24 kg, 3 more than last time")
    }

    @Test
    fun `a challenge under way opens its test`() = liftScenario {
        openTests()
        openTest("Snatch test")
        startTheTest()
        startTheClock()
        // A round every half minute: the challenge counts only sets logged as they were done.
        repeat(5) {
            secondsPass(30)
            tap("Round done")
        }
        secondsPass(153)
        finish()
        closeSummary()
        see("Snatch test: 100 in 5 minutes")
        tap("Snatch test: 100 in 5 minutes")
        see("Start the test")
        see("Best 50 reps · last 50 reps")
    }
}
