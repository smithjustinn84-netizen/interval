@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.today

import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.DEFAULT_BAR_KG
import dev.justinsmith.bells.domain.barbellLifts
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
        val today = TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter, clock = { 1_000L })
        val lift = barbellLifts.first() to false
        assertEquals(DEFAULT_BAR_KG, today.state.value.bells[lift])

        repository.setBar(15.0)
        assertEquals(15.0, today.state.value.bells[lift])
    }

    @Test
    fun `the week and its today move on at midnight with the screen open`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // Sunday night, epoch day 3 (a Sunday), then Monday: a new week.
        var now = 4 * DAY_MILLIS - 1_000
        val today = TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter, clock = { now })
        assertEquals(3L, today.state.value.week.single { it.today }.epochDay)

        now = 4 * DAY_MILLIS
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals(4L, today.state.value.week.single { it.today }.epochDay)
        assertEquals(4L, today.state.value.week.first().epochDay)
        today.viewModelScope.cancel()
    }
}

private const val DAY_MILLIS = 86_400_000L
