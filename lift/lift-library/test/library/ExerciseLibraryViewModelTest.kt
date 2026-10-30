package dev.justinsmith.bells.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseLibraryViewModelTest : MainDispatcherTest() {
    private val repository = FakeBellsStorage().repository()
    private val filters = ExerciseFilterStore()

    private fun library(saved: SavedStateHandle = SavedStateHandle()) =
        ExerciseLibraryViewModel(repository, filters, FixedTimestampFormatter, saved)

    private inline fun ExerciseLibraryViewModel.use(block: ExerciseLibraryViewModel.() -> Unit) = try {
        block()
    } finally {
        viewModelScope.cancel()
    }

    @Test
    fun `an exercise added shows in the list`() = library().use {
        repository.saveExercise(null, ExerciseDraft(name = "Copenhagen plank"))
        assertTrue(state.value.list.exercises.any { it.key == repository.customExercises.value.single().key })
    }

    @Test
    fun `the filters narrow the list and the log sheet shares them`() = library().use {
        updateFilters { it.copy(equipment = setOf(Equipment.Kettlebell)) }
        assertEquals(setOf(Equipment.Kettlebell), state.value.list.filters.equipment)
        assertEquals(setOf(Equipment.Kettlebell), filters.filters.value.equipment)
    }

    @Test
    fun `an exercise opened shows its page until it is closed`() = library().use {
        open(BuiltInExercise.Press.key)
        assertEquals(BuiltInExercise.Press, state.value.detail?.exercise)
        open(null)
        assertNull(state.value.detail)
    }

    @Test
    fun `the exercise open survives the process going`() {
        val saved = SavedStateHandle()
        library(saved).use { open(BuiltInExercise.Press.key) }
        library(saved).use { assertEquals(BuiltInExercise.Press, state.value.detail?.exercise) }
    }

    @Test
    fun `an exercise open that is gone closes its page`() = library().use {
        open("custom:nothing")
        assertNull(state.value.detail)
    }
}
