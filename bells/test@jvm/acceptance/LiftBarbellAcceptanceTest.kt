package dev.justinsmith.bells.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-barbell.xml`: a 20 kg bar under Your equipment, Barbell 5×5 followed, day 1's back squat on
 * the empty bar, then a power clean off the plan with its plates a side, and the summary.
 */
class LiftBarbellAcceptanceTest {
    @Test
    fun `a barbell program starts on the empty bar and a lift off the plan shows its plates`() = liftScenario {
        openEquipment()
        seeChosen("None")
        choose("20 kg")
        goBack()

        followProgram("Barbell 5×5")
        seeProgramDay("Day 1 of 36 · week 1", "Back squat", "Bench press", "Barbell row")
        startProgramDay(1)
        see("Up next · 1 of 15 sets")
        see("20 kg bar, empty")
        see("× 5")
        logNextSet()
        seeSet("20 kg × 5")
        seeMoved("100 kg")
        see("Up next · 2 of 15 sets")

        openLogSheet()
        pickExercise("Power clean", search = "power clean")
        see("20 kg bar, empty")
        barHeavier(16)
        see("20 kg bar · 20 a side")
        barHeavier(1)
        see("20 kg bar · 20 + 1.25 a side")
        logFromSheet()
        seeSet("62.5 kg × 5")
        seeMoved("412.5 kg")
        setsLogged(2)

        finish()
        seeRecord("Back squat", "Heaviest 20 kg × 5")
        seeRecord("Power clean", "Heaviest 62.5 kg × 5")
        closeSummary()
        seeProgramDay("Day 2 of 36 · week 1", "Back squat", "Overhead press", "Barbell deadlift")
    }
}
