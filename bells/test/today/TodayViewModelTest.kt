@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.today

import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.DEFAULT_BAR_KG
import dev.justinsmith.bells.domain.barbellLifts
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
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

class TodayViewModelTest {
    private val storage = FakeBellsStorage()
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
    fun `a barbell lift never done suggests the bar the user owns and follows a change of it`() {
        val today = TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter) { 1_000L }
        val lift = barbellLifts.first() to false
        assertEquals(DEFAULT_BAR_KG, today.state.value.bells[lift])

        repository.setBar(15.0)
        assertEquals(15.0, today.state.value.bells[lift])
    }
}
