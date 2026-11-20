package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartInsightsTest {
    /** A minute of work at [work] then a rest falling [fall] a sample for [restSamples] samples, [rounds] times. */
    private fun intervals(rounds: Int, work: Int = 170, fall: Int = 3, restSamples: Int = 12): List<Int> =
        List(rounds) { List(12) { work } + List(restSamples) { work - fall * (it + 1) } }.flatten()

    /** Each rest of [intervals]: its first and last second. */
    private fun rests(rounds: Int, restSamples: Int = 12): List<IntRange> = List(rounds) { round ->
        val start = (round * (12 + restSamples) + 12) * HR_SAMPLE_SECONDS
        start until start + restSamples * HR_SAMPLE_SECONDS
    }

    @Test
    fun `the drop is from the sample to the one that many seconds on`() {
        val samples = listOf(170, 160, 150, 140, 130, 120, 110)

        assertEquals(30, dropAfter(samples, index = 0, seconds = 15))
        assertEquals(60, dropAfter(samples, index = 0, seconds = 30))
    }

    @Test
    fun `no drop is read from a sample the sensor missed or past the end`() {
        val samples = listOf(170, 160, 0, 140)

        assertNull(dropAfter(samples, index = 0, seconds = 10))
        assertNull(dropAfter(samples, index = 2, seconds = 5))
        assertNull(dropAfter(samples, index = 1, seconds = 60))
    }

    @Test
    fun `no drop is read from the next effort`() {
        val samples = listOf(170, 160, 150, 175)

        assertNull(dropAfter(samples, index = 0, seconds = 15, before = 3))
    }

    @Test
    fun `recovery is the fall over the minute of each rest that long`() {
        val recovery = recovery(intervals(rounds = 3), rests(rounds = 3))

        assertEquals(Recovery(listOf(36, 36, 36), seconds = 60), recovery)
        assertEquals(36, recovery?.average)
    }

    @Test
    fun `rests of half a minute are read over the half minute`() {
        val recovery = recovery(intervals(rounds = 2, restSamples = 6), rests(rounds = 2, restSamples = 6))

        assertEquals(Recovery(listOf(18, 18), seconds = 30), recovery)
    }

    @Test
    fun `rests too short to tell give no recovery`() {
        assertNull(recovery(intervals(rounds = 4, restSamples = 3), rests(rounds = 4, restSamples = 3)))
    }

    @Test
    fun `with no samples there is no recovery`() {
        assertNull(recovery(emptyList(), rests(rounds = 3)))
    }

    @Test
    fun `a last rest that brings it down much less is fading`() {
        assertEquals(RecoveryTrend.Fading, Recovery(listOf(34, 28, 22, 19), seconds = 60).trend)
    }

    @Test
    fun `a last rest about like the first is holding`() {
        assertEquals(RecoveryTrend.Holding, Recovery(listOf(34, 31, 29, 30), seconds = 60).trend)
    }

    @Test
    fun `two rests are too few to call a trend`() {
        assertNull(Recovery(listOf(34, 12), seconds = 60).trend)
    }

    @Test
    fun `load is the minutes in each zone times the zone`() {
        // At a max of 200: two minutes in zone 2 (130) and one in zone 5 (185).
        val samples = List(24) { 130 } + List(12) { 185 }

        assertEquals(2 * 2 + 1 * 5, heartLoad(samples, maxBpm = 200))
    }

    @Test
    fun `samples the sensor missed add no load`() {
        assertEquals(0, heartLoad(List(24) { 0 }, maxBpm = 200))
    }

    @Test
    fun `a peak over the max is offered as the max`() {
        assertEquals(194, peakAbove(listOf(150, 194, 0, 188), maxBpm = 190))
    }

    @Test
    fun `a peak at or under the max is not`() {
        assertNull(peakAbove(listOf(150, 190), maxBpm = 190))
    }

    @Test
    fun `a reading no heart gives is never offered`() {
        assertNull(peakAbove(listOf(150, 245), maxBpm = 190))
    }
}
