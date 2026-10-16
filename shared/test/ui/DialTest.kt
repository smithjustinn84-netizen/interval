package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialTest {
    @Test
    fun ringTicksFillASecondsWorthAtATime() {
        assertEquals(0, litTicks(0f))
        assertEquals(0, litTicks(0.016f))
        assertEquals(1, litTicks(1f / 60))
        assertEquals(18, litTicks(0.3f))
        assertEquals(DIAL_TICKS, litTicks(1f))
    }

    @Test
    fun ringTicksStayInRange() {
        assertEquals(0, litTicks(-0.5f))
        assertEquals(DIAL_TICKS, litTicks(1.5f))
    }

    @Test
    fun handStepsOnceASecondAndWrapsEachMinute() {
        assertEquals(0, dialTick(0))
        assertEquals(0, dialTick(999))
        assertEquals(1, dialTick(1_000))
        assertEquals(59, dialTick(59_999))
        assertEquals(0, dialTick(60_000))
        assertEquals(5, dialTick(125_000))
    }

    @Test
    fun dialTicksAreQuieterThanALine() {
        listOf(lightPalette, darkPalette).forEach { palette ->
            val page = palette.background.luminance()
            val tick = abs(palette.dialTick.luminance() - page)
            val line = abs(palette.line.luminance() - page)
            assertTrue(tick > 0f && tick < line, "dial ticks must show, but more quietly than a line")
        }
    }
}
