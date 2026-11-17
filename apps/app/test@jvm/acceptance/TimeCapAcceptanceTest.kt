package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText
import dev.justinsmith.interval.platform.Speech
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/time-cap.xml`: the Timer made a time cap, on Work's sheet, runs one countdown; each Round done is
 * counted under the ring and Undo takes one back; the cap's end says the rounds, the summary says them in its
 * minutes, and the Log keeps them.
 */
class TimeCapAcceptanceTest {
    @Test
    fun `a time cap counts its rounds and keeps them`() = acceptanceScenario {
        openTimer()
        makeTimeCap()
        setCapTo("5:00")
        // 10 s to get ready, then the five minutes.
        seeTimerTotal("5:10")

        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seePhase("Work")
        seeCapRounds("0 rounds")
        repeat(3) {
            letTimePass(40.seconds)
            tapRoundDone()
        }
        seeCapRounds("3 rounds")
        undoRound()
        seeCapRounds("2 rounds")

        letTimePass(5.minutes)
        heard(Speech.CapOver(2, Speech.Finished))
        see("2 rounds in 5 min")
        see("Saved to your log")
        workoutKeptAsCap(rounds = 2)

        closeSummary()
        leaveTimer()
        openTab("Log")
        see(hasText("2 rounds", substring = true))
    }
}
