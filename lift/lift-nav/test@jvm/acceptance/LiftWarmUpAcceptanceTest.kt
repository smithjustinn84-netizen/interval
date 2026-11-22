package dev.justinsmith.lift.acceptance

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `journeys/lift-warm-up.xml`: a set logged as a warm-up stays in the session, said as one, but counts toward
 * nothing a working set does: it ticks off no planned set, sets no record and adds to no total. The switch is
 * off again for the next set, and a set logged can be made a warm-up, or a working set again, after.
 */
class LiftWarmUpAcceptanceTest {
    @Test
    fun `a warm-up is kept and said but ticks off no planned set`() = liftScenario {
        followProgram("Your first 100 swings")
        startProgramDay(1)
        seeUpNext("1 of 12 sets")
        openLogSheet()
        seeWarmUp(on = false)
        markWarmUp()
        logFromSheet()
        seeSet("16 kg × 10 · Warm-up")
        seeUpNext("1 of 12 sets")
        logNextSet()
        seeUpNext("2 of 12 sets")
        openLogSheet()
        seeWarmUp(on = false)
    }

    @Test
    fun `a warm-up sets no record and adds to no total`() = liftScenario {
        startSession()
        openLogSheet()
        markWarmUp()
        pressSystemBack()
        see("Next set · Warm-up")
        logNextSet()
        seeSet("16 kg × 10 · Warm-up")
        finish()
        seeNo("New records")
        closeSummary()
        val session = app.storage.sessions.sessions.value.single()
        val sets = app.storage.sessions.sets.value.filter { it.sessionUid == session.uid }
        assertEquals(listOf(true), sets.map { it.warmUp })
    }

    @Test
    fun `a set logged is made a warm-up after and back again`() = liftScenario {
        startSession()
        openLogSheet()
        logFromSheet()
        editSet("16 kg × 10")
        markWarmUp()
        saveSet()
        seeSet("16 kg × 10 · Warm-up")
        editSet("16 kg × 10 · Warm-up")
        seeWarmUp(on = true)
        markWarmUp()
        saveSet()
        seeSet("16 kg × 10")
        seeNo("16 kg × 10 · Warm-up")
    }
}
