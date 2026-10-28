package dev.justinsmith.bells.programs

import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgramsViewModelTest : MainDispatcherTest() {
    private val repository = FakeBellsStorage().repository()
    private val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)

    private inline fun programs(block: ProgramsViewModel.() -> Unit) = ProgramsViewModel(repository).run {
        try {
            block()
        } finally {
            viewModelScope.cancel()
        }
    }

    @Test
    fun `nothing is followed to start`() = programs {
        assertNull(state.value.following)
        assertNull(state.value.followingDay)
    }

    @Test
    fun `a program followed shows with its first day`() = programs {
        follow(ladders)
        assertEquals(ladders, state.value.following?.key)
        assertEquals(1, assertNotNull(state.value.followingDay).number)
    }

    @Test
    fun `following the program already followed keeps its place`() = programs {
        follow(ladders)
        val since = repository.following.value
        follow(ladders)
        assertEquals(since, repository.following.value)
    }

    @Test
    fun `stopping leaves nothing followed`() = programs {
        follow(ladders)
        stop()
        assertNull(state.value.following)
    }

    @Test
    fun `a program made shows among the custom ones`() = programs {
        val draft =
            ProgramDraft(name = "Swings", days = listOf(listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 5))))
        repository.saveProgram(null, draft)
        assertEquals(listOf("Swings"), state.value.custom.map { it.name })
    }
}
