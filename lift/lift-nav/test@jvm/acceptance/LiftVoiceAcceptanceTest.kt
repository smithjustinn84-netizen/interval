package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-voice.xml`: Lift's timers speak, never mid-rep. The set's card shows the worst fault its lift is
 * prone to; the rest after a set says one of its faults, then the next lift's set-up in its last ten seconds; an
 * EMOM's count-in says one of the lift's cues, the next minute's the next, and a minute's set done the next fault.
 * Two safety lines take a set-up cue's place: "Groove five light first" before a thrown lift's first set at a bell
 * new to it, said as a program day opens on one, and "Stop a rep early" once a session on a taxed day.
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

    @Test
    fun `a program day opening on a new bell says to groove light first`() = liftScenario {
        followNewDensityProgram("Density")
        tap("Start day 1")
        hear("Groove five light first")
        startTheClock()
        hear("Set the bell a foot in front, hinge at the hips and hike it high between your legs")
    }

    @Test
    fun `a taxed day says stop a rep early once`() = liftScenario(afterAHardSession()) {
        startSession()
        hear("Stop a rep early")
        openLogSheet()
        pickExercise("Snatch", search = "snatch")
        logFromSheet()
        hear("Punch through before it lands")
        secondsPass(81)
        hear("Hike the bell high, drive the hips and pull the elbow back to keep the arc close")
    }

    /** A session finished an hour before the clock's start, said to have felt very hard: today is taxed. */
    private fun afterAHardSession() = FakeLiftStorage().apply {
        val at = -HOUR_MILLIS
        sessions.sets.value += SetEntity(0, "hard", BuiltInExercise.Snatch.key, 24.0, 20, null, at)
        sessions.sessions.value += SessionEntity(
            "hard",
            at - HOUR_MILLIS,
            at,
            "Snatches",
            at,
            effort = PerceivedEffort.VeryHard.level,
        )
    }
}

private const val HOUR_MILLIS = 3_600_000L
