package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/mobility.xml`: Train's Mobility pillar lists the flows; one opens on what is in it, and runs on the
 * workout screen move by move. A finished flow is kept as mobility: it counts toward the week and History names it.
 */
class MobilityAcceptanceTest {
    private companion object {
        /** Wrists and fingers end to end, after the ten seconds to get ready, and a tick over. */
        val FLOW_LENGTH = 301.seconds
    }

    @Test
    fun `a flow started from Train runs move by move and is saved`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        seeMobility()

        openFlow("Wrists and fingers")
        seeMoveInSheet("Finger pulls", "0:30 a side")
        startFlow()

        // Ten seconds to get ready, with the first move named; then the move itself.
        seePhase("Get ready")
        seeMove("Wrist circles", "Move 1 of 5")
        letTimePass(11.seconds)
        seePhase("Move")
        seeNextMove("Palms down, fingers back")

        // Past the circles and the two holds, each with its change-over, into the pulls: the left, then the right.
        letTimePass((30 + 10 + 45 + 10 + 45 + 10).seconds)
        seeMove("Finger pulls", "Move 4 of 5")
        seeSide("Left side")
        letTimePass(30.seconds)
        seePhase("Switch sides")
        letTimePass(5.seconds)
        seeSide("Right side")

        letTimePass((30 + 10 + 65).seconds)
        seeFlowSaved("Wrists and fingers", moves = 5)
        timerIsAsItWas()
    }

    @Test
    fun `a finished flow counts toward the week and is named in History`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        openFlow("Wrists and fingers")
        startFlow()
        seePhase("Get ready")
        letTimePass(FLOW_LENGTH)
        seeFlowSaved("Wrists and fingers", moves = 5)
        closeSummary()

        seeMobility()
        openTab("Today")
        seeThisWeekOnToday("1 mobility")
        seeTodayDoneOnTheWeek()

        openTab("History")
        seeFlowInHistory("Wrists and fingers", "Mobility · 5 moves")
    }

    @Test
    fun `a flow done again from History is the flow again`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        openFlow("Wrists and fingers")
        startFlow()
        seePhase("Get ready")
        letTimePass(FLOW_LENGTH)
        closeSummary()

        openTab("History")
        openWorkoutUnderToday("Wrists and fingers")
        repeatNow()
        seePhase("Get ready")
        seeMove("Wrist circles", "Move 1 of 5")
        timerIsAsItWas()
    }
}
