package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/warm-up-cool-down.xml`: a warm-up and a cool-down set on the Timer run before the first round and
 * after the last, are kept with the workout, and are part of the shape a preset saves.
 */
class WarmUpCoolDownAcceptanceTest {
    @Test
    fun `a warm-up and a cool-down bookend the rounds`() = acceptanceScenario {
        openTimer()
        seeTimer()
        setWarmUp("1:00")
        setCoolDown("2:00")
        // 10 s to get ready, a minute's warm-up, 8 × 30 s on with 7 × 15 s between, two minutes' cool-down.
        seeTimerTotal("8:55")

        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seePhase("Warm-up")
        letTimePass(60.seconds)
        seePhase("Work")
        seeRound("Round 1 of 8")
        letTimePass((8 * 30 + 7 * 15).seconds)
        seePhase("Cool-down")
        letTimePass(120.seconds)
        see("Saved to your log")
        workoutKeptWithBookends(warmUpSeconds = 60, coolDownSeconds = 120)
        closeSummary()

        openAllPresets()
        seeUnsavedTimerCard("1m warm-up · 30s on · 15s off · ×8 · 2m cool-down")
    }

    @Test
    fun `a cool-down ended early still counts the workout as finished`() = acceptanceScenario {
        openTimer()
        setCoolDown("2:00")
        startWorkout()
        seePhase("Get ready")
        letTimePass((11 + 8 * 30 + 7 * 15).seconds)
        seePhase("Cool-down")
        askToEnd()
        confirmEnd()
        see("Saved to your log")
        savedWorkouts(1)
        workoutKeptFinished()
    }
}
