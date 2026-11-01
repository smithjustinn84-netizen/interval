package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-programs.xml`: a program's sheet, what a week of it works; a built-in followed part way made
 * yours in the editor, a week longer, and followed on from the day it was at.
 */
class LiftProgramsAcceptanceTest {
    @Test
    fun `a program's sheet shows what a week of it works and what it leaves out`() = liftScenario {
        // 100 one-arm swings and 10 get-ups a day, five days a week, a side counting half.
        openProgram("Simple & Sinister")
        seeWeekBalance(
            untrained = "Chest, Quads and calves",
            "Glutes and hamstrings: 25 sets worked it most",
            "Shoulders: 25 sets worked it most",
            "Chest: 0 sets worked it most, 0 assisted",
        )
    }

    @Test
    fun `a built-in followed part way is made yours and followed on from its day`() = liftScenario(dayTwoOf()) {
        seeProgramDay("Day 2 of 8 · week 1")
        openProgramToChange("Goblet squat and row")
        makeItYours("Goblet squat and row (yours)")
        see("You're following it: your copy carries on from day 2.")
        aWeekMore()
        see("5")
        saveProgram()
        followingMine("Goblet squat and row (yours)", weeks = 5)
        pressSystemBack()
        seeProgramDay("Day 2 of 10 · week 1")
    }

    /** Following Goblet squat and row with its first day done, so day 2 is next. */
    private fun dayTwoOf() = FakeLiftStorage().apply {
        val program = ProgramId.GobletSquatRow.name
        following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        sessions.sessions.value = listOf(
            SessionEntity("d1", 0, 600_000, "Goblet squat and row", 600_000, programId = program, programDay = 1),
        )
    }
}
