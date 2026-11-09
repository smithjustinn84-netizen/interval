package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.domain.ProgramId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `journeys/lift-program-day.xml`: a free session takes a day of a program from the sheet under its Next set
 * card, and is that day from then on; a day of a program not followed counts for none, and the followed
 * program's next day moves it on.
 */
class LiftProgramDayAcceptanceTest {
    @Test
    fun `a free session takes a day of a program not followed and no program moves on`() = liftScenario {
        startSession()
        see("Next set")
        see("Pick a day of any program to do now")
        doAProgramDay()
        seeNo("Your programs")
        pickProgram("Your first 100 swings")
        seeDays("Day 1", "Day 2", "Day 3")
        see("Deadlift · Two-hand swing")
        seeNo("Next")
        pickDay("Day 2")
        seeUpNext("1 of 12 sets")
        seeNextLiftShown("Deadlift")
        seeNo("Next set")
        seeNo("Do a program day")
        logNextSet()
        finish()
        closeSummary()
        see("Follow a program")
        val session = app.storage.sessions.sessions.value.single()
        assertEquals(ProgramId.FirstHundredSwings.name to 2, session.programId to session.programDay)
    }

    @Test
    fun `a free session takes the followed program's next day and the program moves on`() = liftScenario {
        followProgram("Your first 100 swings")
        seeProgramDay("Day 1 of 12 · week 1")
        startFreeSession()
        doAProgramDay()
        see("Following")
        pickProgram("Your first 100 swings")
        seeDayNext("Day 1")
        pickDay("Day 1")
        seeUpNext("1 of 12 sets")
        logNextSet()
        finish()
        closeSummary()
        seeProgramDay("Day 2 of 12 · week 1")
    }
}
