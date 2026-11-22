package dev.justinsmith.lift.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import dev.justinsmith.lift.data.BellEntity
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * `journeys/lift-program-recap.xml`: a program's last day done, the summary closes onto its recap, which says how
 * much of the plan was done and calls the next cycle as a coach would: a beginner's program done well moves on to
 * the next program for the bells owned, and any other goes again, from the loads last lifted. Each call's button
 * does what it says.
 */
class LiftProgramRecapAcceptanceTest {
    @Test
    fun `a beginner's program done in full moves on to the next one`() = liftScenario(lastDayOf(FIRST)) {
        doTheLastDay(FIRST)
        see(hasText("finished · every day done", substring = true))
        seeTheRecap()
        see("Ready to move on")
        val planned = program(FIRST).day(program(FIRST).totalDays).plannedSets().size
        see("You did $planned of $planned planned sets.")
        secondsPass(60)
        tap(hasText("Follow ", substring = true) and !hasText("Follow again") and hasClickAction())
        waitFor("a program other than the one finished followed") {
            app.storage.following.row.value?.programId.let { it != null && it != FIRST.name }
        }
        assertNotEquals(FIRST.name, app.storage.following.row.value?.programId)
    }

    @Test
    fun `any other program done goes again from the loads last lifted`() = liftScenario(lastDayOf(CLASSIC)) {
        doTheLastDay(CLASSIC)
        seeTheRecap()
        see("Go again, from where you finished")
        see("Each lift starts at the load you last lifted it with.")
        secondsPass(60)
        tap("Follow again")
        waitFor("the program followed again from day 1") { app.storage.following.row.value?.daysBefore == 0 }
        see(hasText("Day 1 of", substring = true))
        assertEquals(ProgramId.GobletSquatRow.name, app.storage.following.row.value?.programId)
    }

    /** [id] followed with every day but its last done before it was followed, and two bells owned. */
    private fun lastDayOf(id: ProgramId) = FakeLiftStorage().apply {
        val days = program(id).totalDays
        following.row.value = FollowingEntity(programId = id.name, startedAtEpochMillis = 0, daysBefore = days - 1)
        bells.rows.value = listOf(BellEntity(16.0), BellEntity(24.0))
    }

    private companion object {
        val FIRST = ProgramId.KettlebellFoundations
        val CLASSIC = ProgramId.GobletSquatRow
    }
}
