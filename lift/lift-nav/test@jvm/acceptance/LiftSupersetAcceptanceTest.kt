package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-superset.xml`: two blocks of a program day made a superset in the program editor, A1 and A2. The
 * session takes them in turn, a set of each round by round. Resting after both, A1 goes straight on to A2 with no
 * rest, A2's set-up said at once, and the pair rests after A2; resting after each, every set rests. Today's plan
 * marks the pair.
 */
class LiftSupersetAcceptanceTest {
    @Test
    fun `a superset resting after both goes straight on from A1 to A2`() = liftScenario(followingSquatAndRow()) {
        makeSquatAndRowASuperset("Rest after both")
        see("A1 · with Row · rest after the pair")
        saveProgram()
        pressSystemBack()

        startProgramDay(1)
        seeUpNext("1 of 9 sets")
        see("A1 · straight on to Row")
        logNextSet()
        seeUpNext("2 of 9 sets")
        see("A2 · with Goblet squat · rest after the pair")
        seeNo("1:30")
        hear("Brace a hand on a bench or your knee with a flat back")

        openTodaysPlan()
        see("A1 · with Row · rest after the pair")
        see("A2 · with Goblet squat · rest after the pair")
    }

    @Test
    fun `the pair rests after A2 and then the next round's A1 is up`() = liftScenario(followingSquatAndRow()) {
        makeSquatAndRowASuperset("Rest after both")
        saveProgram()
        pressSystemBack()

        startProgramDay(1)
        logNextSet()
        // The row's two sides, then the pair's rest.
        logNextSet()
        skipRest()
        logNextSet()
        seeResting("1:30")
        skipRest()
        seeUpNext("4 of 9 sets")
        see("A1 · straight on to Row")
    }

    @Test
    fun `a superset resting after each rests after A1 too`() = liftScenario(followingSquatAndRow()) {
        makeSquatAndRowASuperset("Rest after each")
        see("A1 · with Row · rest after each")
        saveProgram()
        pressSystemBack()

        startProgramDay(1)
        see("A1 · with Row · rest after each")
        logNextSet()
        seeResting("1:30")
        see("A2 · with Goblet squat · rest after each")
    }

    /**
     * Goblet squat and row followed, made yours, its goblet squats (the day's first block) a superset with its rows
     * resting by [rest], as the block's sheet offers it.
     */
    private fun LiftAcceptanceDriver.makeSquatAndRowASuperset(rest: String) {
        openProgramToChange("Goblet squat and row")
        makeItYours("Goblet squat and row (yours)")
        tap("Goblet squat")
        see("Superset with Row")
        tap(rest)
        tap("Done")
    }

    /** Following Goblet squat and row, its first day next. */
    private fun followingSquatAndRow() = FakeLiftStorage().apply {
        following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
    }
}
