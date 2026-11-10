package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-programs.xml`: a program's sheet, what a week of it works and about how long its days take; a
 * built-in followed part way made yours in the editor, a week longer, and followed on from the day it was at.
 */
class LiftProgramsAcceptanceTest {
    @Test
    fun `a program beside the mat and the wall opens with its sheet up and follows`() = liftScenario {
        tap("One bell beside BJJ")
        see("Follow this program")
        tap("Follow this program")
        see("Following")
        see("Day 1 of 12 · week 1")
    }

    @Test
    fun `plus on the strength pillar starts a new program`() = liftScenario {
        tapDescribed("New program")
        see("Save program")
    }

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
    fun `a program's days say about how long they take and so does the day followed`() = liftScenario {
        // Three sets each of three lifts one day, 8 and 7 minutes on the minute the other.
        openProgram("15-minute kettlebell plan")
        seeEstimate("about 15 min")
        followFromSheet()
        seeProgramDay("Day 1 of 18 · week 1")
        seeEstimate("about 15 min")
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
