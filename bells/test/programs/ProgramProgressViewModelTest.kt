@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.programs

import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FixedTimestampFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramProgressViewModelTest {
    private val program = ProgramId.GobletSquatRow.name
    private val storage = FakeBellsStorage().apply {
        following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        sessions.sessions.value = listOf(
            SessionEntity("d1", 0, 600_000, "Goblet squat", 600_000, programId = program, programDay = 1),
        )
        sessions.sets.value = List(3) {
            SetEntity(it + 1L, "d1", BuiltInExercise.GobletSquat.key, 16.0, 10, null, it.toLong())
        }
    }
    private val repository = with(storage) {
        BellsRepository(
            sessions,
            bells,
            following,
            programs,
            exercises,
            bands,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ) { 1_000L }
    }

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `each day done is listed with its date and the sets done of those planned`() {
        val state = ProgramProgressViewModel(repository, FixedTimestampFormatter).state.value
        val row = state.rows.single()
        assertEquals("day 0", row.date)
        assertEquals(1, row.day.number)
        assertEquals(3 to 9, row.day.setsDone to row.day.setsPlanned)
        assertEquals(2, state.progress?.next?.number)
    }

    @Test
    fun `Follow again starts the program over and keeps the run in History`() {
        val viewModel = ProgramProgressViewModel(repository, FixedTimestampFormatter)
        viewModel.followAgain()
        assertEquals(1_000L, storage.following.row.value?.startedAtEpochMillis)
        val state = viewModel.state.value
        assertTrue(state.rows.isEmpty())
        assertEquals(1, state.progress?.next?.number)
        assertEquals(1, repository.sessions.value.size)
    }
}
