@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.history

import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val DAY = 24L * 60 * 60 * 1000

/** A finished session's page: the session, when it was, what it set, and its deletion with undo. */
class LiftDetailViewModelTest {
    private val storage = FakeBellsStorage()
    private val repository = storage.repository()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(uid: String) = LiftDetailViewModel(uid, repository, FixedTimestampFormatter)

    @Test
    fun `the session shows when it finished and the records it set over those before it`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        storage.finished("b", "Heavier swings", DAY, DAY + 60_000, 24.0 to 5, 16.0 to 10)

        val state = viewModel("b").state.value

        assertEquals("Heavier swings", state.session?.title)
        assertEquals("day 1", state.date)
        assertEquals("at ${DAY + 60_000}", state.time)
        assertEquals(listOf(24.0), state.records.map { (it as PersonalRecord.Weighted).heaviestKg })
        assertNull(state.program)
    }

    @Test
    fun `a session that beat nothing set no record`() {
        storage.finished("a", "Swings", 0, 60_000, 24.0 to 10)
        storage.finished("b", "Lighter swings", DAY, DAY + 60_000, 16.0 to 5)

        assertEquals(emptyList(), viewModel("b").state.value.records)
    }

    @Test
    fun `a day of a program names the program`() {
        val key = ProgramKey.BuiltIn(ProgramId.DailySwingsGetUps)
        storage.finished("a", "Day 1", 0, 60_000, 16.0 to 10, program = key)

        assertEquals(key, viewModel("a").state.value.program?.key)
    }

    @Test
    fun `deleting takes the session away and restoring brings it back`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        val viewModel = viewModel("a")

        viewModel.delete()
        assertNull(viewModel.state.value.session)

        viewModel.restore()
        assertEquals("Swings", viewModel.state.value.session?.title)
    }

    @Test
    fun `a session never there is an empty page`() {
        assertEquals(LiftDetailUiState(), viewModel("gone").state.value)
    }
}
