package dev.justinsmith.lift.acceptance

import kotlin.test.Test

/** `journeys/lift-programs.xml`: a program's sheet, what a week of it works. */
class LiftProgramsAcceptanceTest {
    @Test
    fun `a program's sheet shows what a week of it works and what it leaves out`() = liftScenario {
        openProgram("Full body for beginners")
        seeWeekBalance(
            untrained = "Chest",
            "Glutes and hamstrings: ",
            "Back: ",
            "Chest: 0 sets worked it most, 0 assisted",
        )
    }
}
