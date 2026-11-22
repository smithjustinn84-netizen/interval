package dev.justinsmith.interval.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareCardLogicTest {
    @Test
    fun `share heart rate filters sensor gaps and reports peak and average`() {
        val hr = ShareHeartRate(samples = listOf(0, 120, 150, 0, 180, 999))
        assertTrue(hr.hasChart)
        assertEquals(listOf(120, 150, 180), hr.validSamples)
        assertEquals(180, hr.peakBpm)
        assertEquals(150, hr.averageBpm)
    }

    @Test
    fun `share heart rate with fewer than two valid readings draws no chart`() {
        val hr = ShareHeartRate(samples = listOf(0, 145, 0))
        assertFalse(hr.hasChart)
        assertEquals(145, hr.peakBpm)
    }

    @Test
    fun `next pose progress cycles through iconic stops and resets to default still`() {
        val first = nextPoseProgress(null)
        assertNotNull(first)
        val second = nextPoseProgress(first)
        assertNotNull(second)
        assertTrue(second > first)
        val third = nextPoseProgress(second)
        assertNotNull(third)
        assertTrue(third > second)
        assertNull(nextPoseProgress(third))
    }
}
