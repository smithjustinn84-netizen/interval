@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.settings

import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingClassSensor
import dev.justinsmith.interval.fakes.SENSOR_NAME
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SensorSettingsViewModelTest : MainDispatcherTest() {
    private val sensor = RecordingClassSensor(SensorStatus.Unpaired)
    private val viewModel by lazy { SensorSettingsViewModel(sensor) }

    @Test
    fun `pairing takes the sensor nearby`() {
        assertEquals(SensorSettingsUiState(SensorStatus.Unpaired), viewModel.state.value)

        viewModel.pair()

        assertEquals(SensorSettingsUiState(SensorStatus.Paired(SENSOR_NAME)), viewModel.state.value)
    }

    @Test
    fun `a pairing that finds none says so once and is no longer looking`() = runTest {
        sensor.nearby = null

        viewModel.pair()

        assertEquals(SensorFailure.NotFound, viewModel.failures.first())
        assertEquals(SensorSettingsUiState(SensorStatus.Unpaired), viewModel.state.value)
    }

    @Test
    fun `a second tap while it looks does nothing`() {
        val held = CompletableDeferred<Unit>()
        sensor.holding = held

        viewModel.pair()
        viewModel.pair()
        assertEquals(SensorSettingsUiState(SensorStatus.Unpaired, looking = true), viewModel.state.value)
        held.complete(Unit)

        assertEquals(1, sensor.pairings)
        assertEquals(SensorSettingsUiState(SensorStatus.Paired(SENSOR_NAME)), viewModel.state.value)
    }

    @Test
    fun `forgetting lets the sensor go`() {
        sensor.status.value = SensorStatus.Paired(SENSOR_NAME)

        viewModel.forget()

        assertEquals(SensorSettingsUiState(SensorStatus.Unpaired), viewModel.state.value)
    }
}
