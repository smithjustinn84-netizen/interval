@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.LiveHeartRateState.Reading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A radio that hears the sensors it is given and connects to whichever it is asked, a channel of frames each. */
private class FakeRadio : HeartRateRadio {
    var nearby: List<HeardSensor> = emptyList()
    val connected = mutableListOf<String>()
    var frames = Channel<HeartRateFrame>(Channel.UNLIMITED)

    override fun scan() = flow {
        nearby.forEach { emit(it) }
        awaitCancellation()
    }

    override fun connect(id: String) = flow {
        connected += id
        frames.consumeAsFlow().collect { emit(it) }
    }
}

private class FakeStore : PairedSensorStore {
    override var sensor: NearbySensor? = null
    override var battery: Int? = null

    override fun pair(id: String, name: String) {
        sensor = NearbySensor(id, name)
        battery = null
    }

    override fun clear() {
        sensor = null
        battery = null
    }
}

private class FakeAccess : SensorAccess {
    override var supported = true
    override var allowed = true
    var on = true

    override suspend fun ready() = on
}

class StreamingSensorTest {
    private val radio = FakeRadio()
    private val store = FakeStore()
    private val access = FakeAccess()

    private fun TestScope.sensor() = StreamingSensor(radio, store, access, backgroundScope)

    private val strap = HeardSensor("A1", "Wahoo TICKR", rssi = -70)
    private val armband = HeardSensor("B2", "Polar Sense 1234ABCD", rssi = -50)

    @Test
    fun aDeviceWithoutBluetoothOffersNoSensor() = runTest {
        access.supported = false
        assertEquals(SensorStatus.Unavailable, sensor().status.value)
    }

    @Test
    fun theOneSensorHeardIsPaired() = runTest {
        radio.nearby = listOf(strap)
        val sensor = sensor()
        assertEquals(SensorStatus.Unpaired, sensor.status.value)

        assertNull(sensor.pair { error("Only one was heard") })

        assertEquals(SensorStatus.Paired("Wahoo TICKR", recordsClasses = false), sensor.status.value)
        assertEquals(NearbySensor("A1", "Wahoo TICKR"), store.sensor)
    }

    @Test
    fun aPolarSensorRecordsNoClassHere() = runTest {
        radio.nearby = listOf(armband)
        val sensor = sensor()
        sensor.pair { null }
        assertEquals(SensorStatus.Paired("Polar Sense 1234ABCD", recordsClasses = false), sensor.status.value)
        assertEquals(SensorFailure.Failed, sensor.startRecording())
        assertEquals(SensorFailure.NothingRecorded, sensor.sync { error("Nothing to keep") })
    }

    @Test
    fun noneHeardIsNotFound() = runTest {
        val sensor = sensor()
        assertEquals(SensorFailure.NotFound, sensor.pair { null })
        assertEquals(SensorStatus.Unpaired, sensor.status.value)
    }

    @Test
    fun twoHeardAreOfferedNearestFirst() = runTest {
        radio.nearby = listOf(strap, armband)
        val sensor = sensor()
        var offered: List<NearbySensor> = emptyList()
        sensor.pair { sensors ->
            offered = sensors
            sensors.last()
        }
        assertEquals(listOf("B2", "A1"), offered.map { it.id })
        assertEquals("A1", store.sensor?.id)
    }

    @Test
    fun noPickTakesNone() = runTest {
        radio.nearby = listOf(strap, armband)
        val sensor = sensor()
        assertNull(sensor.pair { null })
        assertNull(store.sensor)
        assertEquals(SensorStatus.Unpaired, sensor.status.value)
    }

    @Test
    fun bluetoothOffOrRefusedIsNoAccess() = runTest {
        radio.nearby = listOf(strap)
        access.on = false
        assertEquals(SensorFailure.NoAccess, sensor().pair { null })
        access.on = true
        access.allowed = false
        assertEquals(SensorFailure.NoAccess, sensor().pair { null })
        assertNull(store.sensor)
    }

    @Test
    fun thePairedSensorStreamsAndSaysItsBattery() = runTest {
        radio.nearby = listOf(strap)
        val sensor = sensor()
        sensor.pair { null }
        sensor.start(this)
        runCurrent()
        assertEquals(listOf("A1"), radio.connected)

        radio.frames.trySend(HeartRateFrame.Battery(80))
        radio.frames.trySend(HeartRateFrame.Measured(Reading(132)))
        runCurrent()

        assertEquals(Reading(132), sensor.reading.value)
        assertEquals(80, sensor.battery.value)
        assertEquals(80, store.battery)
        sensor.stop(this)
    }

    @Test
    fun aSensorRefusedBluetoothIsNotStreamed() = runTest {
        store.pair("A1", "Wahoo TICKR")
        access.allowed = false
        val sensor = sensor()
        sensor.start(this)
        runCurrent()
        assertEquals(emptyList(), radio.connected)
        sensor.stop(this)
    }

    @Test
    fun forgettingLetsTheSensorAndItsBatteryGo() = runTest {
        store.pair("A1", "Wahoo TICKR")
        store.battery = 40
        val sensor = sensor()
        assertEquals(40, sensor.battery.value)

        sensor.forget()

        assertEquals(SensorStatus.Unpaired, sensor.status.value)
        assertNull(sensor.battery.value)
        assertNull(store.sensor)
    }

    @Test
    fun aNewPairingForgetsTheLastBattery() = runTest {
        store.pair("A1", "Wahoo TICKR")
        store.battery = 40
        radio.nearby = listOf(armband)
        val sensor = sensor()
        sensor.pair { null }
        assertNull(sensor.battery.value)
        assertEquals("B2", store.sensor?.id)
    }
}
