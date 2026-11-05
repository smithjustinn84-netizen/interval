package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Today's plan on a program day: each change kept with the session, built on the one before. */
class TodayPlanEditorTest {
    private val repository = FakeLiftStorage().repository()
    private val editor = TodayPlanEditor(repository.sets, repository.programs, repository.gear)
    private val goblets = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)

    private val blocks get() = assertNotNull(repository.sessions.active.value?.plan).blocks

    private fun startDay() {
        repository.programs.follow(goblets)
        repository.sessions.startSession(goblets)
    }

    @Test
    fun `a block changed skipped and planned again is kept with the session`() {
        startDay()
        // Today's plan is the day's as written until something changes it.
        val rounds = assertNotNull(repository.programs.programDay.value).today.blocks.first().block.rounds

        editor.changeBlock(0) { it.copy(rounds = it.rounds + 1) }
        assertEquals(rounds + 1, blocks.first().block.rounds)

        editor.skipBlock(0)
        assertTrue(blocks.first().skipped)
        editor.unskipBlock(0)
        assertTrue(!blocks.first().skipped)
    }

    @Test
    fun `another lift swapped in takes the block's sets still to do`() {
        startDay()
        editor.swapBlock(0, BuiltInExercise.FrontSquat)
        assertEquals(BuiltInExercise.FrontSquat, blocks.first().block.exercise)
    }

    @Test
    fun `the next load stepped twice builds on the first step`() {
        startDay()
        val start = assertNotNull(editor.next(repository.sessions.active.value, emptyList(), SetDraft())).load.kg

        editor.stepNextLoad(1, emptyList(), SetDraft())
        val once = assertNotNull(editor.next(repository.sessions.active.value, emptyList(), SetDraft())).load.kg
        editor.stepNextLoad(1, emptyList(), SetDraft())
        val twice = assertNotNull(editor.next(repository.sessions.active.value, emptyList(), SetDraft())).load.kg

        assertTrue(start < once && once < twice)
        assertEquals(twice, blocks.first().block.load?.kg)
    }

    @Test
    fun `a free session has nothing up next and no plan to change`() {
        repository.sessions.startSession(null)
        assertNull(editor.next(repository.sessions.active.value, emptyList(), SetDraft()))

        editor.changeBlock(0) { it.copy(rounds = it.rounds + 1) }
        assertNull(repository.sessions.active.value?.plan)
    }
}
