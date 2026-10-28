package dev.justinsmith.bells.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseEditorViewModelTest : MainDispatcherTest() {
    private val repository = FakeBellsStorage().repository()

    private fun editor(uid: String? = null, name: String = "", saved: SavedStateHandle = SavedStateHandle()) =
        ExerciseEditorViewModel(repository, saved, uid, name)

    private inline fun ExerciseEditorViewModel.use(block: ExerciseEditorViewModel.() -> Unit) = try {
        block()
    } finally {
        viewModelScope.cancel()
    }

    @Test
    fun `a new exercise starts from the name searched for`() = editor(name = "  Turkish sit-up ").use {
        assertEquals("Turkish sit-up", state.value.draft.name)
        assertFalse(state.value.saved)
    }

    @Test
    fun `a name taken while the sheet is open cannot be saved`() = editor().use {
        assertFalse("turkish sit-up" in state.value.taken)
        repository.saveExercise(null, ExerciseDraft(name = "Turkish sit-up"))
        assertTrue(state.value.taken.isNotEmpty())
        assertFalse(save("Turkish sit-up", ""))
        assertEquals(1, repository.customExercises.value.size)
    }

    @Test
    fun `an exercise with sets logged keeps its equipment and measure`() {
        val uid = repository.saveExercise(null, ExerciseDraft(name = "Copenhagen plank", measure = Measure.Time))
        editor(uid).use {
            assertFalse(state.value.locked)
            repository.startSession()
            repository.logSet(repository.customExercises.value.single(), 0.0, 0, null, seconds = 30)
            assertTrue(state.value.locked)
            change { it.copy(equipment = Equipment.Kettlebell, measure = Measure.Reps, name = "Side plank") }
            assertEquals(Measure.Time, state.value.draft.measure)
            assertEquals("Side plank", state.value.draft.name)
        }
    }

    @Test
    fun `archiving follows the exercise as saved`() {
        val uid = repository.saveExercise(null, ExerciseDraft(name = "Copenhagen plank"))
        editor(uid).use {
            toggleArchived()
            assertTrue(state.value.archived)
            toggleArchived()
            assertFalse(state.value.archived)
        }
    }

    @Test
    fun `a draft left in saved state comes back until the sheet is opened again`() {
        val saved = SavedStateHandle()
        editor(name = "Sit-up", saved = saved).use { change { it.copy(name = "Get-up", measure = Measure.Time) } }
        editor(name = "Sit-up", saved = saved).use {
            assertEquals(ExerciseDraft(name = "Get-up", measure = Measure.Time), state.value.draft)
            reopen()
            assertEquals(ExerciseDraft(name = "Sit-up"), state.value.draft)
        }
    }
}
