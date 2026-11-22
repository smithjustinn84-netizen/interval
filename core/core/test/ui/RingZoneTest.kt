package dev.justinsmith.interval.ui

import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.LiveHeartRateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingZoneTest {
    @Test
    fun `a bezel is stronger a zone at a time up to full at the top one`() {
        val strengths = HeartRateZone.entries.map(::zoneStrength)
        assertEquals(strengths.sorted(), strengths)
        assertEquals(strengths.size, strengths.distinct().size)
        assertTrue(strengths.first() > 0.3f, "the easiest zone still shows")
        assertEquals(1f, strengths.last())
    }

    @Test
    fun `a reading with contact is zoned by the max heart rate`() {
        assertEquals(HeartRateZone.Zone1, ringZoneOf(LiveHeartRateState.Reading(bpm = 100), maxBpm = 190, null))
        assertEquals(HeartRateZone.Zone4, ringZoneOf(LiveHeartRateState.Reading(bpm = 158), maxBpm = 190, null))
        assertEquals(HeartRateZone.Zone5, ringZoneOf(LiveHeartRateState.Reading(bpm = 158), maxBpm = 170, null))
    }

    @Test
    fun `a resting heart rate zones it by the reserve`() {
        val reading = LiveHeartRateState.Reading(bpm = 120)
        assertEquals(HeartRateZone.Zone2, ringZoneOf(reading, maxBpm = 190, restingBpm = null))
        assertEquals(HeartRateZone.Zone1, ringZoneOf(reading, maxBpm = 190, restingBpm = 60))
    }

    @Test
    fun `no heart read is no zone`() {
        assertNull(ringZoneOf(LiveHeartRateState.Unavailable, maxBpm = 190, null))
        assertNull(ringZoneOf(LiveHeartRateState.Idle, maxBpm = 190, null))
        assertNull(ringZoneOf(LiveHeartRateState.Connecting, maxBpm = 190, null))
        assertNull(ringZoneOf(LiveHeartRateState.Disconnected, maxBpm = 190, null))
        assertNull(ringZoneOf(LiveHeartRateState.Reading(bpm = 0, contact = false), maxBpm = 190, null))
    }
}
