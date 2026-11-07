package dev.justinsmith.lift.acceptance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `journeys/lift-plan-changes.xml`: day 1 of Goblet squat and row changed in the session (a heavier bell, a
 * set changed after logging, the rows skipped), and the change kept in a copy followed from day 2. Then a day
 * done in full that felt all out: its steps up wait, and leaving keeps the program as it was. And the ABC either
 * way: *20 minutes beside BJJ*'s ABC on the minute taken as many as you can for today, the program untouched.
 */
class LiftPlanChangesAcceptanceTest {
    @Test
    fun `a day changed in the session is kept in a copy of the program`() = liftScenario {
        followProgram("Goblet squat and row")
        startProgramDay(1)
        see("Up next · 1 of 9 sets")
        see("16 kg")
        seeNextLiftShown("Goblet squat")

        changeUpNext("24")
        see("24 kg")
        logNextSet()
        see("Up next · 2 of 9 sets")
        seeSet("24 kg × 10")
        seeMoved("240 kg")

        addARepTo("24 kg × 10")
        seeSet("24 kg × 11")
        seeMoved("264 kg")

        openTodaysPlan()
        see("Changed")
        see("1 of 3 done")
        skipBlock("Row")
        see("Up next · 2 of 3 sets")

        tap("Finish")
        see("Keep today's changes?")
        see("3 × 10 (10–15) → 3 × 10 (10–15) · 24 kg")
        tap("Every day")
        tap("Save as Goblet squat and row (yours)")
        see("Saved as Goblet squat and row (yours). You follow it from day 2.")
        val saved = app.storage.programs.rows.value.single()
        assertEquals("Goblet squat and row (yours)", saved.name)
        assertTrue(saved.days.contains("\"kg\":24.0"), saved.days)

        closeSummary()
        seeProgramDay("Day 2 of 8 · week 1", "Goblet squat and row (yours)", "Start day 2")
    }

    @Test
    fun `a day done in full that felt all out leaves its steps up off`() = liftScenario {
        followProgram("Goblet squat and row")
        startProgramDay(1)
        repeat(9) { logNextSet() }
        tap("Finish")
        see("Next time")
        see("3 × 10 (10–15) → 3 × 11 (10–15)")
        see("Save as Goblet squat and row (yours)")

        sayHowItWas("All out", "Cooked")
        see("It felt very hard: these wait until you turn them on.")
        seeNo("Save as Goblet squat and row (yours)")
        closeSummary()
        seeNo("Keep today's changes?")
        seeProgramDay("Day 2 of 8 · week 1", "Goblet squat and row", "Start day 2")
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }

    @Test
    fun `the ABC on the minute is taken as many as you can for today and the program keeps it on the minute`() =
        liftScenario {
            followProgram("20 minutes beside BJJ")
            tap("Start day 1")
            see("Start EMOM")
            openTodaysPlan()
            tap("Armor Building Complex")
            tap("As many as you can")
            see("On the minute")
            see("Minutes")
            tap("Done")
            see("Up next · 15 min cap")
            see("Start the clock")
            assertTrue(app.storage.programs.rows.value.isEmpty())
            startTheClock()
            roundsDone(2, total = 2)
        }
}
