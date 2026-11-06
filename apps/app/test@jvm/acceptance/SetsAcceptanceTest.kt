package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.platform.Speech
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/sets.xml`: sets of rounds set on the Timer run each set's rounds with a longer rest between the sets,
 * say the set as they go, and are kept with the workout as its shape.
 */
class SetsAcceptanceTest {
    @Test
    fun `two sets of three rounds rest between the sets and finish`() = acceptanceScenario {
        openTimer()
        seeTimer()
        lowerRoundsTo(3)
        setSets("2")
        setRestBetweenSets(seconds = 60)
        // 10 s to get ready, then twice 3 × 30 s on with 2 × 15 s between, and a minute between the two.
        seeTimerTotal("5:10")

        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seePhase("Work")
        seeRound("Set 1 of 2 · Round 1 of 3")
        letTimePass((3 * 30 + 2 * 15).seconds)
        seePhase("Set rest")
        heard(Speech.SetRest)
        letTimePass(60.seconds)
        seePhase("Work")
        seeRound("Set 2 of 2 · Round 1 of 3")
        heard(Speech.Work(lastRound = false, set = 2))
        letTimePass((3 * 30 + 2 * 15).seconds)
        see("Saved to your log")
        see("6/6")
        workoutKeptWithSets(sets = 2, rounds = 3)
    }
}
