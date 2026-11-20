@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.sensor

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingClassSensor
import dev.justinsmith.interval.fakes.SENSOR_NAME
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.sparringRecording
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClassSensorViewModelTest : MainDispatcherTest() {
    private val started = noonOf(TODAY)
    private val storage = FakeStorage()
    private val repository = storage.repository()
    private val sensor = RecordingClassSensor(SensorStatus.Paired(SENSOR_NAME), now = { started })

    private val viewModel by lazy { ClassSensorViewModel(sensor, repository.history, FixedTimestampFormatter) }

    @Test
    fun `with no sensor paired there is no card`() {
        sensor.status.value = SensorStatus.Unpaired
        assertNull(viewModel.state.value.card)

        sensor.status.value = SensorStatus.Unavailable
        assertNull(viewModel.state.value.card)
    }

    @Test
    fun `a paired sensor is set recording and says since when`() = runTest {
        assertEquals(SensorCard.Ready(SENSOR_NAME), viewModel.state.value.card)

        viewModel.record()

        assertEquals(ClassSensorEvent.Started, viewModel.events.first())
        assertEquals(SensorCard.Recording(FixedTimestampFormatter.time(started)), viewModel.state.value.card)
        assertEquals(1, sensor.starts)
    }

    @Test
    fun `a sync keeps the class with its rounds and logs its day`() = runTest {
        sensor.status.value = SensorStatus.Recording(SENSOR_NAME, started)
        sensor.recorded = sparringRecording(rounds = 4, startedAtEpochMillis = started)

        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Synced(started), viewModel.events.first())
        val kept = repository.history.classes.value.single()
        assertEquals(4, kept.rounds.size)
        assertEquals(Sport.Bjj to TODAY, kept.sport to kept.epochDay)
        assertEquals(listOf(Sport.Bjj to TODAY), repository.history.sportDays.value.map { it.sport to it.epochDay })
        assertEquals(SensorCard.Ready(SENSOR_NAME), viewModel.state.value.card)
    }

    @Test
    fun `a class goes on the day it started even when it is synced the day after`() = runTest {
        val yesterday = noonOf(TODAY - 1)
        sensor.recorded = sparringRecording(rounds = 2, startedAtEpochMillis = yesterday)

        viewModel.sync(Sport.Bjj)

        viewModel.events.first()
        assertEquals(TODAY - 1, repository.history.classes.value.single().epochDay)
        assertEquals(listOf(TODAY - 1), repository.history.sportDays.value.map { it.epochDay })
    }

    @Test
    fun `a sensor out of reach says so and keeps and logs nothing`() = runTest {
        sensor.status.value = SensorStatus.Recording(SENSOR_NAME, started)
        sensor.recorded = sparringRecording(rounds = 4, startedAtEpochMillis = started)
        sensor.failing = SensorFailure.NotFound

        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Failed(SensorFailure.NotFound), viewModel.events.first())
        assertEquals(emptyList(), repository.history.classes.value)
        assertEquals(emptyList(), repository.history.sportDays.value)
        // The class is on the sensor still.
        assertEquals(SensorCard.Recording(FixedTimestampFormatter.time(started)), viewModel.state.value.card)
    }

    @Test
    fun `a recording with no heart rate read is not kept`() = runTest {
        sensor.recorded = ClassRecording(started, List(100) { ClassSample(bpm = 0, movement = 0.2f) })

        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Failed(SensorFailure.NothingRecorded), viewModel.events.first())
        assertEquals(emptyList(), repository.history.classes.value)
        assertEquals(emptyList(), repository.history.sportDays.value)
    }

    @Test
    fun `a class that cannot be stored stays on the sensor to sync again`() = runTest {
        sensor.status.value = SensorStatus.Recording(SENSOR_NAME, started)
        sensor.recorded = sparringRecording(rounds = 4, startedAtEpochMillis = started)
        storage.classes.failWrites = true

        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Failed(SensorFailure.Failed), viewModel.events.first())
        assertEquals(emptyList(), repository.history.sportDays.value)
        assertEquals(SensorCard.Recording(FixedTimestampFormatter.time(started)), viewModel.state.value.card)

        storage.classes.failWrites = false
        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Synced(started), viewModel.events.first())
        assertEquals(4, repository.history.classes.value.single().rounds.size)
        assertEquals(SensorCard.Ready(SENSOR_NAME), viewModel.state.value.card)
    }

    @Test
    fun `a sensor with nothing on it says so and stands ready again`() = runTest {
        sensor.status.value = SensorStatus.Recording(SENSOR_NAME, started)

        viewModel.sync(Sport.Bjj)

        assertEquals(ClassSensorEvent.Failed(SensorFailure.NothingRecorded), viewModel.events.first())
        assertEquals(SensorCard.Ready(SENSOR_NAME), viewModel.state.value.card)
    }

    @Test
    fun `a second tap while the sensor is being reached does nothing`() = runTest {
        val held = CompletableDeferred<Unit>()
        sensor.holding = held

        viewModel.record()
        viewModel.record()
        assertEquals(true, viewModel.state.value.busy)
        held.complete(Unit)

        assertEquals(ClassSensorEvent.Started, viewModel.events.first())
        assertEquals(1, sensor.starts)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun `a recording that would not start says why`() = runTest {
        sensor.failing = SensorFailure.NoAccess

        viewModel.record()

        assertEquals(ClassSensorEvent.Failed(SensorFailure.NoAccess), viewModel.events.first())
        assertEquals(SensorCard.Ready(SENSOR_NAME), viewModel.state.value.card)
    }
}
