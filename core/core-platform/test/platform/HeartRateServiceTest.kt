package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.LiveHeartRateState.Reading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartRateServiceTest {
    private fun measurement(vararg bytes: Int) =
        HeartRateService.measurement(ByteArray(bytes.size) { bytes[it].toByte() })

    @Test
    fun `a heart rate in one byte from a sensor that can't tell contact reads as it is`() {
        assertEquals(Reading(72), measurement(0x00, 72))
    }

    @Test
    fun `a heart rate in two bytes is read little-endian`() {
        assertEquals(Reading(180), measurement(0x01, 180, 0))
        // Past one byte: 0x0102 is 258, more than a heart beats, so no contact.
        assertEquals(Reading(0, contact = false), measurement(0x01, 0x02, 0x01))
    }

    @Test
    fun `a byte over 127 is still a heart rate`() {
        assertEquals(Reading(200), measurement(0x00, 200))
    }

    @Test
    fun `contact detected reads and contact lost reads as none`() {
        assertEquals(Reading(140), measurement(0x06, 140))
        assertEquals(Reading(0, contact = false), measurement(0x04, 140))
        // Detected without being supported: the sensor said nothing it can stand by, so the number stands.
        assertEquals(Reading(140), measurement(0x02, 140))
    }

    @Test
    fun `energy and the beats' intervals after the heart rate change nothing`() {
        // Flags: two-byte heart rate, contact detected and supported, energy expended, RR intervals.
        assertEquals(Reading(150), measurement(0x1F, 150, 0, 0x10, 0x02, 0x90, 0x01, 0x88, 0x01))
        assertEquals(Reading(95), measurement(0x16, 95, 0x90, 0x01))
    }

    @Test
    fun `a number no heart beats at is no contact`() {
        assertEquals(Reading(0, contact = false), measurement(0x00, 0))
        assertEquals(Reading(0, contact = false), measurement(0x00, 12))
    }

    @Test
    fun `bytes too short to say a heart rate are none`() {
        assertNull(measurement())
        assertNull(measurement(0x00))
        assertNull(measurement(0x01, 150))
    }

    @Test
    fun `a battery level is one byte of a percentage`() {
        assertEquals(82, HeartRateService.batteryLevel(byteArrayOf(82)))
        assertEquals(100, HeartRateService.batteryLevel(byteArrayOf(100)))
        assertNull(HeartRateService.batteryLevel(byteArrayOf()))
        assertNull(HeartRateService.batteryLevel(byteArrayOf(0xC8.toByte())))
    }
}
