package dev.justinsmith.interval.ui

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A shader that fails to compile is no error where it is drawn: the ring and the clock fall back to their plain
 * drawing, as they must on a platform with no shaders. So here, where Skia compiles them, each must.
 */
class ShaderSourcesTest {
    @Test
    fun `the ring's shader compiles`() {
        assertNotNull(shaderProgram(RING_SKSL))
    }

    @Test
    fun `the clock's shader compiles`() {
        assertNotNull(shaderProgram(CLOCK_SKSL))
    }

    @Test
    fun `the sensor pulse's shader compiles`() {
        assertNotNull(shaderProgram(SENSOR_PULSE_SHADER))
    }

    @Test
    fun `the heart rate chart's shader compiles`() {
        assertNotNull(shaderProgram(HEART_RATE_CHART_SHADER))
    }

    @Test
    fun `a source that is no shader gives none`() {
        assertNull(shaderProgram("half4 main(float2 p) { return nothing; }"))
    }
}
