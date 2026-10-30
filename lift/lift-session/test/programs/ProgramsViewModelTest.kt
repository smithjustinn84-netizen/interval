package dev.justinsmith.lift.programs

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgramsViewModelTest : MainDispatcherTest() {
    private val repository = FakeLiftStorage().repository()
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
