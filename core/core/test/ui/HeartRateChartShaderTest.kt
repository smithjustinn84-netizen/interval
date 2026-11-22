package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Size
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.heartRateZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartRateChartShaderTest {
    @Test
    fun zoneThresholdsAreStrictlyAscendingAndAlignWithHeartRateZone() {
        for (resting in listOf(null, 55, 70)) {
            val thresholds = heartZoneThresholds(DEFAULT_MAX_BPM, resting)
            assertTrue(thresholds.zone2 < thresholds.zone3)
            assertTrue(thresholds.zone3 < thresholds.zone4)
            assertTrue(thresholds.zone4 < thresholds.zone5)

            assertEquals(HeartRateZone.Zone1, heartRateZone(thresholds.zone2.toInt() - 1, DEFAULT_MAX_BPM, resting))
            assertEquals(HeartRateZone.Zone2, heartRateZone(thresholds.zone2.toInt() + 1, DEFAULT_MAX_BPM, resting))
            assertEquals(HeartRateZone.Zone3, heartRateZone(thresholds.zone3.toInt() + 1, DEFAULT_MAX_BPM, resting))
            assertEquals(HeartRateZone.Zone4, heartRateZone(thresholds.zone4.toInt() + 1, DEFAULT_MAX_BPM, resting))
            assertEquals(HeartRateZone.Zone5, heartRateZone(thresholds.zone5.toInt() + 1, DEFAULT_MAX_BPM, resting))
        }
    }

    @Test
    fun thresholdProjectionKeepsOffScreenCutoffsUnclamped() {
        val thresholds = heartZoneThresholds(maxBpm = 190, restingBpm = null)
        // High-effort workout staying in Zone 4-5 (155..182 bpm)
        val projected = heartChartThresholdsY(
            thresholds = thresholds,
            minY = 147f,
            maxY = 190f,
            height = 112f,
            strokeWidth = 2.5f,
        )
        // Higher zones map to smaller Y coordinates (closer to top of canvas)
        assertTrue(projected.zone5 < projected.zone4)
        assertTrue(projected.zone4 < projected.zone3)
        assertTrue(projected.zone3 < projected.zone2)
        // Off-screen lower zone boundaries stay below the chart bottom so the shader reads the bottom as Zone 3/4
        assertTrue(projected.zone2 > 112f)
        assertTrue(projected.zone3 > 112f)
        assertTrue(projected.zone4 in 0f..112f)
        assertTrue(projected.zone5 in 0f..112f)
    }

    @Test
    fun validSampleRunsSplitsAcrossGapsAndSkipsSinglePointIslands() {
        assertEquals(emptyList(), validSampleRuns(emptyList()))
        assertEquals(emptyList(), validSampleRuns(listOf(0, 120, 0, 145, 0)))
        assertEquals(listOf(0..3), validSampleRuns(listOf(110, 125, 140, 155)))
        assertEquals(listOf(1..3, 5..8), validSampleRuns(listOf(0, 110, 125, 140, 0, 130, 145, 160, 175, 0)))
    }

    @Test
    fun validSampleRunsHandlesInteriorGapAndIsolatedSample() {
        val samples = listOf(0, 110, 125, 140, 0, 135, 0, 148, 162, 175, 0)
        assertEquals(listOf(1..3, 7..9), validSampleRuns(samples))
    }

    @Test
    fun shaderProgramCompilesAndBindsAllUniformsWhenSupported() {
        val program = shaderProgram(HEART_RATE_CHART_SHADER) ?: return
        val thresholds = heartZoneThresholds(DEFAULT_MAX_BPM, restingBpm = null)
        val thresholdsY = heartChartThresholdsY(
            thresholds = thresholds,
            minY = 96f,
            maxY = 186f,
            height = 112f,
            strokeWidth = 2.5f,
        )
        bindHeartChartShader(
            program = program,
            size = Size(320f, 112f),
            density = 2f,
            thresholdsY = thresholdsY,
            zones = darkPalette.heartZones,
            surfaceColor = darkPalette.surface,
        )
        bindHeartChartShader(
            program = program,
            size = Size(320f, 24f),
            density = 3f,
            thresholdsY = thresholdsY,
            zones = lightWorkoutColors.heartZones,
            surfaceColor = lightWorkoutColors.ground,
        )
    }

    @Test
    fun fallbackBrushHandlesNormalAndOffScreenThresholds() {
        val thresholds = heartZoneThresholds(DEFAULT_MAX_BPM, restingBpm = 60)
        val normalY = heartChartThresholdsY(thresholds, minY = 90f, maxY = 186f, height = 112f, strokeWidth = 2.5f)
        val offScreenY = heartChartThresholdsY(thresholds, minY = 160f, maxY = 185f, height = 24f, strokeWidth = 2.5f)

        heartChartFallbackBrush(112f, normalY, darkPalette.heartZones, darkPalette.surface)
        heartChartFallbackBrush(24f, offScreenY, lightPalette.heartZones, lightPalette.surface)
        heartChartFallbackBrush(0f, offScreenY, darkWorkoutColors.heartZones, darkWorkoutColors.ground)
    }
}
