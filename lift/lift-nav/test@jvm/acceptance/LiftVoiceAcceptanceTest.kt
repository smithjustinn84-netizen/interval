package dev.justinsmith.lift.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-voice.xml`: Lift's timers speak, never mid-rep. The set's card shows the worst fault its lift is
 * prone to; the rest after a set says one of its faults, then the next lift's set-up in its last ten seconds; an
 * EMOM's count-in says one of the lift's cues, the next minute's the next, and a minute's set done the next fault.
 */
class LiftVoiceAcceptanceTest {
    @Test
    fun `a rest and an EMOM coach the lift aloud`() = liftScenario {
        startSession()
        seeWatchFor("Hips back, shins stay vertical")

        openLogSheet()
        pickExercise("Snatch", search = "snatch")
        logFromSheet()
        seeResting("1:30")
        hear("Punch through before it lands")
        secondsPass(81)
        hear("Hike the bell high, drive the hips and pull the elbow back to keep the arc close")
        secondsPass(9)
        seeNextLiftShown("Snatch")
        seeWatchFor("Punch through before it lands")

        openLogSheet()
        startEmom()
        secondsPass(2)
        hear("Hike the bell high, drive the hips and pull the elbow back to keep the arc close")
        secondsPass(9)
        emomSetDone()
        hear("Hips back, shins stay vertical")
        secondsPass(50)
        hear("Open the hand near the top and punch through so the bell settles softly overhead")
    }
}
