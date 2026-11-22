@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.LiveHeartRateState.Connecting
import dev.justinsmith.interval.platform.LiveHeartRateState.Disconnected
import dev.justinsmith.interval.platform.LiveHeartRateState.Idle
import dev.justinsmith.interval.platform.LiveHeartRateState.Reading
import dev.justinsmith.interval.platform.LiveHeartRateState.Unavailable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A radio that connects to whatever it is asked, each connection a channel the test sends frames down. */
private class FakeLink : HeartRateLink {
    class Connection(val id: String) {
        val frames = Channel<HeartRateFrame>(Channel.UNLIMITED)
        var open = true

        fun read(bpm: Int, contact: Boolean = true) = frames.trySend(HeartRateFrame.Measured(Reading(bpm, contact)))

        /** The sensor goes out of reach. */
        fun drop() = frames.close()

        fun fail() = frames.close(IllegalStateException("GATT error 133"))
    }

    val connections = mutableListOf<Connection>()
    val open: List<Connection> get() = connections.filter { it.open }
    val latest: Connection get() = connections.last()

    override fun connect(id: String) = flow {
        val connection = Connection(id).also { connections += it }
        try {
            connection.frames.consumeAsFlow().collect { emit(it) }
        } finally {
            connection.open = false
        }
    }
}

class HeartRateStreamTest {
    private val link = FakeLink()
    private var sensor: String? = "AA:BB"
    private val batteries = mutableListOf<Int>()

    /** The stream, run in the test's background, which ends with it. */
    private fun TestScope.stream(): HeartRateStream =
        HeartRateStream(
            link,
            sensorId = { sensor },
            idle = { if (sensor == null) Unavailable else Idle },
            scope = backgroundScope,
            onBattery = { batteries += it },
        ).also { runCurrent() }

    private fun TestScope.end(stream: HeartRateStream) {
        stream.stopAll()
        runCurrent()
    }

    @Test
    fun `nothing connects until someone holds the stream`() = runTest {
        val stream = stream()
        assertEquals(Idle, stream.reading.value)
        assertTrue(link.connections.isEmpty())
    }

    @Test
    fun `a holder streams the paired sensor's readings and every one of them is read`() = runTest {
        val stream = stream()
        val read = mutableListOf<Int>()
        backgroundScope.launch { stream.readings.collect { read += it } }
        stream.start("workout")
        runCurrent()
        assertEquals(Connecting, stream.reading.value)
        assertEquals("AA:BB", link.latest.id)

        link.latest.read(120)
        link.latest.read(120)
        link.latest.read(121)
        runCurrent()

        assertEquals(Reading(121), stream.reading.value)
        // A heart held steady is read each time, though the reading shown doesn't change.
        assertEquals(listOf(120, 120, 121), read)
        end(stream)
    }

    @Test
    fun `a reading without contact is shown and never read`() = runTest {
        val stream = stream()
        val read = mutableListOf<Int>()
        backgroundScope.launch { stream.readings.collect { read += it } }
        stream.start("workout")
        runCurrent()

        link.latest.read(0, contact = false)
        runCurrent()

        assertEquals(Reading(0, contact = false), stream.reading.value)
        assertEquals(emptyList(), read)
        end(stream)
    }

    @Test
    fun `the connection is let go only once its last holder stops`() = runTest {
        val stream = stream()
        stream.start("workout")
        stream.start("lift")
        runCurrent()
        assertEquals(1, link.connections.size)

        stream.stop("workout")
        runCurrent()
        assertEquals(1, link.open.size)

        stream.stop("lift")
        runCurrent()
        assertTrue(link.open.isEmpty())
        assertEquals(Idle, stream.reading.value)
    }

    @Test
    fun `a holder joining or leaving while another holds it keeps the one connection`() = runTest {
        val stream = stream()
        stream.start("pulse")
        runCurrent()
        stream.start("workout")
        runCurrent()
        stream.stop("pulse")
        runCurrent()

        assertEquals(1, link.connections.size)
        assertEquals(1, link.open.size)
        end(stream)
    }

    @Test
    fun `a new holder asks again for a sensor that couldn't be streamed from`() = runTest {
        sensor = null
        val stream = stream()
        stream.start("pulse")
        runCurrent()
        assertTrue(link.connections.isEmpty())

        // Bluetooth switched on since, say: the next to hold the stream gets it going.
        sensor = "AA:BB"
        stream.start("workout")
        runCurrent()
        assertEquals(1, link.open.size)
        end(stream)
    }

    @Test
    fun `a dropped connection says so and is tried again`() = runTest {
        val stream = stream()
        stream.start("workout")
        runCurrent()
        link.latest.read(130)
        link.latest.drop()
        runCurrent()
        assertEquals(Disconnected, stream.reading.value)

        advanceTimeBy(HeartRateStream.RETRY_MILLIS + 1)
        assertEquals(2, link.connections.size)
        link.latest.read(128)
        runCurrent()
        assertEquals(Reading(128), stream.reading.value)
        end(stream)
    }

    @Test
    fun `a sensor that keeps failing is tried less and less often`() = runTest {
        val stream = stream()
        stream.start("workout")
        runCurrent()
        link.latest.fail()
        runCurrent()

        // Five seconds doubled: ten before the second try.
        advanceTimeBy(HeartRateStream.RETRY_MILLIS * 2 - 1)
        assertEquals(1, link.connections.size)
        advanceTimeBy(2)
        assertEquals(2, link.connections.size)

        link.latest.fail()
        runCurrent()
        advanceTimeBy(HeartRateStream.RETRY_MILLIS * 4 + 1)
        assertEquals(3, link.connections.size)
        end(stream)
    }

    @Test
    fun `with no sensor to stream from nothing connects`() = runTest {
        sensor = null
        val stream = stream()
        stream.start("workout")
        runCurrent()

        assertTrue(link.connections.isEmpty())
        assertEquals(Unavailable, stream.reading.value)

        // Paired now: the stream asked again comes up.
        sensor = "AA:BB"
        stream.settle()
        runCurrent()
        assertEquals(1, link.open.size)
        end(stream)
    }

    @Test
    fun `work taking the sensor waits for the stream to let it go and gives it back after`() = runTest {
        val stream = stream()
        stream.start("workout")
        runCurrent()
        link.latest.read(110)
        runCurrent()

        var took = false
        backgroundScope.launch {
            stream.take()
            took = true
        }
        runCurrent()
        assertTrue(took)
        assertTrue(link.open.isEmpty())
        assertEquals(Connecting, stream.reading.value)

        stream.giveBack()
        runCurrent()
        assertEquals(2, link.connections.size)
        assertEquals(1, link.open.size)
        end(stream)
    }

    @Test
    fun `taken with no one holding it the stream stays idle after`() = runTest {
        val stream = stream()
        stream.take()
        stream.giveBack()
        runCurrent()

        assertTrue(link.connections.isEmpty())
        assertEquals(Idle, stream.reading.value)
    }

    @Test
    fun `the sensor's battery is heard as it says it`() = runTest {
        val stream = stream()
        stream.start("workout")
        runCurrent()
        link.latest.frames.trySend(HeartRateFrame.Battery(76))
        runCurrent()

        assertEquals(listOf(76), batteries)
        assertFalse(stream.reading.value is Reading)
        end(stream)
    }
}
