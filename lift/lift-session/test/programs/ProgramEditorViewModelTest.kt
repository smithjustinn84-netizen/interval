@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.programs

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The program editor: a new program's draft, saving, changing one already saved, deleting it, and the saved state. */
class ProgramEditorViewModelTest {
    private val storage = FakeLiftStorage()
    private val repository = storage.repository()
    private val filters = ExerciseFilterStore()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun editor(uid: String? = null, savedState: SavedStateHandle = SavedStateHandle()) =
        ProgramEditorViewModel(
            repository.programs,
            repository.exercises,
            repository.gear,
            repository.sessions,
            filters,
            uid,
            savedState,
        )

    @Test
    fun `a new program starts from the default draft and cannot be saved with an empty day`() {
        val editor = editor()

        assertFalse(editor.state.value.saved)
        assertEquals(ProgramDraft(), editor.state.value.draft)
        assertFalse(editor.save("Mine"))
        assertEquals(emptyList(), repository.programs.customPrograms.value)
    }

    @Test
    fun `a draft with a block on every day is saved then changed under its uid and deleted`() {
        val editor = editor()
        editor.change { it.addBlock(0) }

        assertTrue(editor.save("Mine"))
        val program = repository.programs.customPrograms.value.single()
        assertEquals("Mine", program.name)

        val again = editor(uid = (program.key as ProgramKey.Custom).uid)
        assertTrue(again.state.value.saved)
        assertEquals(ProgramDraft.of(program), again.state.value.draft)
        again.change { it.copy(weeks = 8) }
        assertTrue(again.save("Mine still"))
        assertEquals(listOf("Mine still" to 8), repository.programs.customPrograms.value.map { it.name to it.weeks })

        again.delete()
        assertEquals(emptyList(), repository.programs.customPrograms.value)
    }

    @Test
    fun `changes survive in the saved state`() {
        val handle = SavedStateHandle()
        editor(savedState = handle).change { it.addBlock(0).copy(daysPerWeek = 4, weeks = 6) }

        val restored = editor(savedState = handle).state.value.draft

        assertEquals(4, restored.daysPerWeek)
        assertEquals(6, restored.weeks)
        assertEquals(listOf(1), restored.days.map { it.size })
    }

    @Test
    fun `a program gone meanwhile is made afresh`() {
        val editor = editor(uid = "gone")

        assertFalse(editor.state.value.saved)
        assertEquals(ProgramDraft(), editor.state.value.draft)
    }

    @Test
    fun `a copy of a built-in starts as it is under a name not taken`() {
        repository.programs.saveProgram(
            null,
            ProgramDraft(name = "Easy Strength (yours)", days = listOf(listOf(ProgramDraft.NEW_BLOCK))),
        )
        val editor = copyEditor(ProgramId.EasyStrength, "Easy Strength (yours)")

        val easy = program(ProgramId.EasyStrength)
        assertEquals(ProgramDraft.of(easy).copy(name = "Easy Strength (yours) 2"), editor.state.value.draft)
        assertFalse(editor.state.value.saved)
        assertNull(editor.state.value.carriesOnFrom)
        assertTrue(editor.save("Easy Strength (yours) 2"))
        assertEquals(
            easy.days,
            repository.programs.customPrograms.value.single {
                it.name == "Easy Strength (yours) 2"
            }.days,
        )
        assertNull(repository.programs.following.value)
    }

    @Test
    fun `a copy of the built-in followed carries on from its day`() {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(key)
        repository.sessions.startSession(key)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        repository.sessions.finishSession("Goblet squat")
        val editor = copyEditor(ProgramId.GobletSquatRow, "Goblet squat and row (yours)")

        assertEquals(2, editor.state.value.carriesOnFrom)
        assertTrue(editor.save("Mine"))
        assertEquals("Mine", repository.programs.following.value?.name)
        assertEquals(2, repository.programs.programDay.value?.number)
    }

    @Test
    fun `an exercise lifted while the editor is open is offered among the recent`() {
        val editor = editor()
        assertEquals(emptyList(), editor.state.value.choices.library.recents)

        repository.sessions.startSession(null)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        repository.sessions.finishSession("Goblet squat")

        assertEquals(listOf(BuiltInExercise.GobletSquat), editor.state.value.choices.library.recents)
    }

    /** A copy of [id], named from [base] as the screen words it: a common test can't load the screen's words. */
    private fun copyEditor(id: ProgramId, base: String) =
        ProgramEditorViewModel(
            repository.programs,
            repository.exercises,
            repository.gear,
            repository.sessions,
            filters,
            uid = null,
            copy = ProgramCopy(id, base),
        )
}
