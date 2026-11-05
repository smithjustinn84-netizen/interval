package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/ladder.xml`: a ladder set on the Timer, in Work's sheet, works each round a step longer than the one
 * before, says how long the coming round is, and is kept with the workout as its shape.
 */
class LadderAcceptanceTest {
    @Test
    fun `a climbing ladder works each round longer`() = acceptanceScenario {
        openTab("Timer")
        seeTimer()
        lowerRoundsTo(3)
        setLadder(seconds = 10)
        // 10 s to get ready, then 30, 40 and 50 s on with 15 s between.
        seeTimerTotal("2:40")

        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seePhase("Work")
        see("0:29")
        letTimePass(30.seconds)
        seePhase("Rest")
        see("Next: round 2 of 3 · 0:40")
        letTimePass(15.seconds)
        seeRound("Round 2 of 3")
        see("0:39")
        letTimePass((39 + 15 + 50).seconds)
        see("Saved to history")
        workoutKeptAsLadder(step = 10)
    }
}
