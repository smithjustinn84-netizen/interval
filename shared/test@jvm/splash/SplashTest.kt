@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.StateRestoration
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplashTest {
    @Test
    fun `opens on the splash screen's mark on ground`() = runUiTest {
        mainClock.autoAdvance = false
        // Unthemed, as App() hosts it.
        setContent { Splash(ready = false, onFinish = {}) }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(PhaseColors.ground, pixels[2, 2])
        // The middle of the Work arc, 288dp across the 108-unit canvas at density 1.
        val unit = 288f / Mark.CANVAS
        val angle = Math.toRadians(160.0)
        val x = Window.Phone.width / 2f + (Mark.RING_RADIUS * unit * sin(angle)).toFloat()
        val y = Window.Phone.height / 2f - (Mark.RING_RADIUS * unit * cos(angle)).toFloat()
        assertEquals(PhaseColors.work, pixels[x.roundToInt(), y.roundToInt()])
    }

    @Test
    fun `holds until the app is ready, then opens onto it and finishes`() = runUiTest {
        mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        var finished = false
        setThemedContent { Splash(ready = ready, onFinish = { finished = true }) }
        mainClock.advanceTimeBy(5_000)
        assertFalse(finished)
        assertEquals(PhaseColors.ground, onRoot().captureToImage().toPixelMap()[2, 2])

        ready = true
        mainClock.advanceTimeBy(1_000)
        assertTrue(finished)
    }

    @Test
    fun `plays through in under two seconds when the app is ready at once`() = runUiTest {
        mainClock.autoAdvance = false
        var finished = false
        setThemedContent { Splash(ready = true, onFinish = { finished = true }) }
        mainClock.advanceTimeBy(2_000)
        assertTrue(finished)
    }

    @Test
    fun `leaves the app showing once played, and doesn't play again after a restore`() = runUiTest {
        mainClock.autoAdvance = false
        val restoration = StateRestoration(this)
        restoration.setContent {
            WithIntro(ready = true) { Box(Modifier.fillMaxSize().background(Color.Red)) }
        }
        assertEquals(PhaseColors.ground, onRoot().captureToImage().toPixelMap()[2, 2])
        mainClock.advanceTimeBy(2_000)
        assertEquals(Color.Red, onRoot().captureToImage().toPixelMap()[2, 2])

        restoration.emulateSaveAndRestore()
        assertEquals(Color.Red, onRoot().captureToImage().toPixelMap()[2, 2])
    }

    private fun assertEquals(expected: Color, actual: Color) =
        assertEquals(expected.copy(alpha = 1f), actual.copy(alpha = 1f), "$actual is not $expected")
}
