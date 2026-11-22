package dev.justinsmith.lift.acceptance

import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.lift.fakes.FakeIntervalActivity
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
        sayHowItWas("Hard", "Grip went")
        closeSummary()
        sessionKeptAs(3, "Grip went")
        seeProgramDay("Day 2 of 36 · week 1", "Back squat", "Overhead press", "Barbell deadlift")
    }

    @Test
    fun `a barbell session in pounds uses a 45 lb bar 5 lb steps and US plates`() = liftScenario(
        intervals = FakeIntervalActivity().apply { weightUnit.value = WeightUnit.Lb },
    ) {
        openEquipment()
        seeChosen("None")
        choose("45 lb")
        goBack()

        followProgram("Barbell 5×5")
        startProgramDay(1)
        see("45 lb bar, empty")
        logNextSet()
        seeSet("45 lb × 5")
        seeMoved("225 lb")

        openLogSheet()
        pickExercise("Power clean", search = "power clean")
        see("45 lb bar, empty")
        barHeavier(18)
        see("45 lb bar · 45 a side")
        barHeavier(1)
        see("45 lb bar · 45 + 2.5 a side")
        logFromSheet()
        seeSet("140 lb × 5")
        seeMoved("925 lb")

        finish()
        seeRecord("Back squat", "Heaviest 45 lb × 5")
        seeRecord("Power clean", "Heaviest 140 lb × 5")
    }
}
