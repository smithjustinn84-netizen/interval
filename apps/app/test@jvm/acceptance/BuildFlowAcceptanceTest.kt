package dev.justinsmith.interval.acceptance

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/build-flow.xml`: a mobility flow of your own, built on Train's Mobility pillar from the library's mat
 * moves and holds or moves typed in, or from a copy of a shipped one. It is listed with the shipped flows, runs on
 * the workout screen as they do, and History names it. Two flows never share their moves and times.
 */
class BuildFlowAcceptanceTest {
    @Test
    fun `a flow built from the library and a move of your own runs and is named in history`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        seeMobility()
        buildAFlow()
        nameFlow("Floor core")
        addLibraryMove("Plank")
        addOwnMove("Dead bug", "Low back down, opposite arm and leg out.", steps = 2)
        saveFlow("Floor core")
        flowsStored("Floor core")
        flowHolds("Floor core", "Plank" to 30, "Dead bug" to 40)

        openFlow("Floor core")
        seeMoveInSheet("Dead bug", "0:40")
        startFlow()
        seePhase("Get ready")
        seeMove("Plank", "Move 1 of 2")
        // Ten seconds to get ready, the plank, ten to change, the dead bug, and a tick over.
        letTimePass((10 + 30 + 10 + 40 + 1).seconds)
        seeFlowSaved("Floor core", moves = 2)
        timerIsAsItWas()
    }

    @Test
    fun `a shipped flow made yours is kept beside it once a time is changed`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        openFlow("Hips and shoulders")
        makeItYours()
        // The copy is the shipped flow move for move, so it can't be told from it until something changes.
        seeSaveRefused("Hips and shoulders")
        openMove("90/90 hip switch")
        fiveSecondsMore()
        closeMove()
        saveFlow("Hips and shoulders (yours)")
        flowsStored("Hips and shoulders (yours)")
        see("Hips and shoulders")
    }

    @Test
    fun `a flow deleted comes back with undo`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Mobility")
        buildAFlow()
        nameFlow("Plank only")
        addLibraryMove("Plank")
        saveFlow("Plank only")

        openFlow("Plank only")
        editFlow()
        deleteFlow()
        flowsStored()
        seeNo("Plank only")
        undoNotice()
        flowsStored("Plank only")
        see("Plank only")
    }
}
