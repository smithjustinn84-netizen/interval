package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveHeartRateTest {
    @Test
    fun `heartRateZone classifies all five zones against max bpm`() {
        val max = 200
        assertEquals(HeartRateZone.Zone1, heartRateZone(100, max))
        assertEquals(HeartRateZone.Zone1, heartRateZone(119, max))
        assertEquals(HeartRateZone.Zone2, heartRateZone(120, max))
        assertEquals(HeartRateZone.Zone2, heartRateZone(139, max))
        assertEquals(HeartRateZone.Zone3, heartRateZone(140, max))
        assertEquals(HeartRateZone.Zone3, heartRateZone(159, max))
        assertEquals(HeartRateZone.Zone4, heartRateZone(160, max))
        assertEquals(HeartRateZone.Zone4, heartRateZone(179, max))
        assertEquals(HeartRateZone.Zone5, heartRateZone(180, max))
        assertEquals(HeartRateZone.Zone5, heartRateZone(205, max))
    }

    @Test
    fun `a sensor is in use once it is connecting`() {
        assertEquals(false, LiveHeartRateState.Unavailable.inUse)
        assertEquals(false, LiveHeartRateState.Idle.inUse)
        assertEquals(true, LiveHeartRateState.Connecting.inUse)
        assertEquals(true, LiveHeartRateState.Reading(120).inUse)
        assertEquals(true, LiveHeartRateState.Disconnected.inUse)
    }

    @Test
    fun `zoneSeconds tallies valid samples and skips zero gaps`() {
        val samples = listOf(100, 130, 0, 165, 185, 190)
        val totals = zoneSeconds(samples, maxBpm = 200, sampleSeconds = 5)
        assertEquals(5, totals[HeartRateZone.Zone1])
        assertEquals(5, totals[HeartRateZone.Zone2])
        assertEquals(0, totals[HeartRateZone.Zone3])
        assertEquals(5, totals[HeartRateZone.Zone4])
        assertEquals(10, totals[HeartRateZone.Zone5])
    }

    @Test
    fun `ZoneTracker requires sustained crossing and enforces cooldown`() {
        val tracker = ZoneTracker(maxBpm = 200, sustainMillis = 5_000L, cooldownMillis = 15_000L)
        // Initial reading establishes Recovery band, never fires.
        assertNull(tracker.record(110, 0L))
        // Brief spike to High (3s) then back to Recovery -> no transition.
        assertNull(tracker.record(170, 1_000L))
        assertNull(tracker.record(172, 4_000L))
        assertNull(tracker.record(115, 5_000L))

        // Sustained High for 5s -> fires EnteredHigh.
        assertNull(tracker.record(170, 10_000L))
        assertNull(tracker.record(175, 14_000L))
        assertEquals(ZoneTransition.EnteredHigh, tracker.record(176, 15_000L))

        // Immediately dropping back to Recovery within cooldown (15s) updates band silently without cueing.
        assertNull(tracker.record(110, 16_000L))
        assertNull(tracker.record(110, 21_000L))

        // After cooldown (>= 30_000L), sustained High fires again.
        assertNull(tracker.record(175, 26_000L))
        assertEquals(ZoneTransition.EnteredHigh, tracker.record(178, 31_000L))

        // After cooldown (>= 46_000L), sustained Recovery fires EnteredRecovery.
        assertNull(tracker.record(110, 42_000L))
        assertEquals(ZoneTransition.EnteredRecovery, tracker.record(108, 47_000L))
    }

    @Test
    fun `HeartRateSampler buckets readings by 5s and computes average and peak`() {
        val sampler = HeartRateSampler(sampleSeconds = 5)
        assertFalse(sampler.hasReadings)
        assertNull(sampler.summary())
        assertEquals(emptyList(), sampler.samples())

        // Out-of-range readings ignored.
        sampler.add(10, 1_000L)
        sampler.add(300, 2_000L)
        assertFalse(sampler.hasReadings)

        // Bucket 0: 120, 130 -> avg 125
        sampler.add(120, 10_000L)
        sampler.add(130, 12_000L)
        // Bucket 1: gap -> 0
        // Bucket 2: 160, 180 -> avg 170
        sampler.add(160, 20_000L)
        sampler.add(180, 23_000L)

        assertTrue(sampler.hasReadings)
        assertEquals(HeartRate(averageBpm = 148, peakBpm = 180), sampler.summary())
        assertEquals(listOf(125, 0, 170), sampler.samples())
    }

    @Test
    fun `with a resting heart rate the zones are cut from the reserve over it`() {
        // Max 190 over a rest of 50 is a reserve of 140: zone 2 from 134 and zone 5 from 176.
        assertEquals(HeartRateZone.Zone1, heartRateZone(133, maxBpm = 190, restingBpm = 50))
        assertEquals(HeartRateZone.Zone2, heartRateZone(134, maxBpm = 190, restingBpm = 50))
        assertEquals(HeartRateZone.Zone5, heartRateZone(176, maxBpm = 190, restingBpm = 50))
        // By the max alone 133 is zone 3 already.
        assertEquals(HeartRateZone.Zone3, heartRateZone(133, maxBpm = 190))
    }

    @Test
    fun `a resting heart rate that leaves no reserve is left out`() {
        assertEquals(heartRateZone(110, maxBpm = 120), heartRateZone(110, maxBpm = 120, restingBpm = 100))
    }

    @Test
    fun `time in zone and the tracker go by the reserve too`() {
        val samples = List(12) { 130 }

        assertEquals(60, zoneSeconds(samples, maxBpm = 190, restingBpm = 50)[HeartRateZone.Zone1])
        assertEquals(60, zoneSeconds(samples, maxBpm = 190)[HeartRateZone.Zone2])
        // 160 is zone 4 by the max and zone 3 over a rest of 50: no rise into high effort to cue.
        val tracker = ZoneTracker(maxBpm = 190, restingBpm = 50)
        assertNull(tracker.record(110, 0L))
        assertNull(tracker.record(160, 1_000L))
        assertNull(tracker.record(160, 7_000L))
    }

    @Test
    fun `a sampler taken up again keeps what it had from the same start`() {
        val sampler = HeartRateSampler(sampleSeconds = 5)
        sampler.restore(originMillis = 100_000L, kept = listOf(0, 120, 150))

        assertEquals(3, sampler.size)
        sampler.add(160, 116_000L)

        assertEquals(listOf(0, 120, 150, 160), sampler.samples())
        assertEquals(HeartRate(averageBpm = 143, peakBpm = 160), sampler.summary())
    }
}
