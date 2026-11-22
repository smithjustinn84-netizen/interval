package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.platform.HeartRateZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensorPulseShaderTest {
    @Test
    fun zoneProfilesIncreaseRippleFrequencyAndIntensityMonotonicallyAcrossZones() {
        val bpms = listOf(95, 122, 142, 162, 182)
        val profiles = HeartRateZone.entries.zip(bpms) { zone, bpm ->
            pulseZoneProfile(bpm = bpm, zone = zone, recording = false)
        }
        for (index in 1..<profiles.size) {
            val calmer = profiles[index - 1]
            val harder = profiles[index]
            assertTrue(
                harder.waveSpan > calmer.waveSpan,
                "Zone ${index + 1} ripple frequency should exceed Zone $index",
            )
            assertTrue(
                harder.intensity > calmer.intensity,
                "Zone ${index + 1} color intensity should exceed Zone $index",
            )
            assertTrue(
                harder.beatsPerSecond > calmer.beatsPerSecond,
                "Higher BPM should drive faster pulse cadence",
            )
        }
        profiles.forEach { profile ->
            assertEquals(1f, profile.active)
        }
    }

    @Test
    fun cadenceTracksLiveBpmAndClampsOutliers() {
        assertEquals(1f, pulseZoneProfile(bpm = 60, zone = HeartRateZone.Zone1, recording = false).beatsPerSecond)
        assertEquals(2f, pulseZoneProfile(bpm = 120, zone = HeartRateZone.Zone2, recording = false).beatsPerSecond)
        assertEquals(3f, pulseZoneProfile(bpm = 180, zone = HeartRateZone.Zone5, recording = false).beatsPerSecond)

        val tooLow = pulseZoneProfile(bpm = 10, zone = HeartRateZone.Zone1, recording = false)
        val minClamped = pulseZoneProfile(bpm = 40, zone = HeartRateZone.Zone1, recording = false)
        assertEquals(minClamped.beatsPerSecond, tooLow.beatsPerSecond)

        val tooHigh = pulseZoneProfile(bpm = 300, zone = HeartRateZone.Zone5, recording = false)
        val maxClamped = pulseZoneProfile(bpm = 220, zone = HeartRateZone.Zone5, recording = false)
        assertEquals(maxClamped.beatsPerSecond, tooHigh.beatsPerSecond)
    }

    @Test
    fun disconnectedAndRecordingStatesFallBackToIdleBreathing() {
        val disconnected = pulseZoneProfile(bpm = null, zone = null, recording = false)
        val recording = pulseZoneProfile(bpm = 148, zone = HeartRateZone.Zone3, recording = true)
        val noZone = pulseZoneProfile(bpm = 120, zone = null, recording = false)

        for (idle in listOf(disconnected, recording, noZone)) {
            assertEquals(0f, idle.active)
            assertEquals(IDLE_BREATH_HZ, idle.beatsPerSecond)
            assertTrue(idle.intensity < pulseZoneProfile(95, HeartRateZone.Zone1, recording = false).intensity)
        }
    }

    @Test
    fun shaderProgramCompilesAndBindsAllUniformsWhenSupported() {
        val program = shaderProgram(SENSOR_PULSE_SHADER) ?: return
        program.set("uCenter", 16f, 16f)
        program.set("uMaxRadius", 46f)
        program.set("uCoreRadius", 4f)
        program.set("uIdleMinRadius", 11.5f)
        program.set("uIdleMaxRadius", 16f)
        program.set("uPhase", INITIAL_PULSE_PHASE)
        program.set("uWaveSpan", 2.45f)
        program.set("uIntensity", 0.70f)
        program.set("uActive", 1f)
        program.set("uStrokeWidth", 1.4f)
        program.set("uColor", Color(0xFFFF7A43))
        program.brush()
    }
}
