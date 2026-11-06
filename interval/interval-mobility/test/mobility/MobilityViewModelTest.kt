package dev.justinsmith.interval.mobility

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.builtInFlows
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobilityViewModelTest : MainDispatcherTest() {
    private val repository = FakeStorage().repository()

    @Test
    fun `the flows are listed by when each is for with how long each runs`() {
        val state = mobilityState(prepSeconds = 10)

        assertEquals(listOf("Hips and shoulders" to "8:00", "Wrists and fingers" to "5:00"), state.before.named())
        assertEquals(
            listOf("Spine and hamstrings" to "10:00", "Core on the mat" to "18:00", "Full reset" to "25:00"),
            state.after.named(),
        )
        assertEquals(builtInFlows.count { it.use == FlowUse.Before }, state.before.size)
        assertEquals("Full reset", state.item("full-reset")?.flow?.name)
        assertNull(state.item("gone"))
        assertNull(state.item(null))
    }

    @Test
    fun `the totals follow the get-ready countdown as it is set`() {
        repository.settings.updateConfig { it.copy(prepSeconds = 0) }
        val viewModel = MobilityViewModel(repository.settings, repository.flows)

        assertEquals(0, viewModel.state.value.prepSeconds)
        assertEquals("7:50", viewModel.state.value.before.first().total)

        repository.settings.updateConfig { it.copy(prepSeconds = 30) }
        assertEquals(30, viewModel.state.value.prepSeconds)
        assertEquals("8:20", viewModel.state.value.before.first().total)
        viewModel.viewModelScope.cancel()
    }

    private fun List<FlowItem>.named() = map { it.flow.name to it.total }
}
