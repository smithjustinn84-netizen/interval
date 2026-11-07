@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartButtonTest {
    private val haptics = RecordingHaptics()
    private var starts = 0
    private var background = Color.Unspecified

    /** Start at the foot of a page of the background colour, with room above it for its ring. */
    private fun DesktopComposeUiTest.setStart() {
        setThemedContent {
            background = IntervalTheme.palette.background
            WithHaptics(haptics) {
                Box(Modifier.fillMaxSize().background(background)) {
                    StartButton(
                        { starts++ },
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.space16),
                    )
                }
            }
        }
    }

    /** Whether anything is drawn on the page's centre line above the button, clear of its shadow. */
    private fun DesktopComposeUiTest.drawnAbove(): Boolean {
        val bounds = onNodeWithText("Start workout").getBoundsInRoot()
        val image = onRoot().captureToImage().toPixelMap()
        val x = ((bounds.left + bounds.right) / 2).value.toInt()
        val clearOfShadow = (bounds.top - SHADOW_REACH).value.toInt()
        return (0 until clearOfShadow).any { y -> image[x, y] != background }
    }

    @Test
    fun `Start fires once and confirms under the thumb`() = runUiTest {
        setStart()
        onNodeWithText("Start workout").performClick()
        assertEquals(1, starts)
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed)
    }

    @Test
    fun `pressed Start sends a ring out past the button and is still over the page as the workout rises`() =
        runUiTest {
            mainClock.autoAdvance = false
            setStart()
            mainClock.advanceTimeByFrame()
            assertFalse(drawnAbove(), "nothing above Start before it is pressed")
            onNodeWithText("Start workout").performClick()
            // Halfway through the rise the ring is well past the button and not yet faded.
            mainClock.advanceTimeBy(Motion.LAUNCH_MILLIS / 2L)
            assertTrue(drawnAbove(), "the ring above Start")
            mainClock.advanceTimeBy(Motion.LAUNCH_MILLIS.toLong() + SETTLE_MILLIS)
            assertFalse(drawnAbove(), "the ring spent")
            assertEquals(1, starts)
        }

    private companion object {
        /** Further than Start's glow reaches above it. */
        val SHADOW_REACH = 24.dp

        /** Slack after an animation's last frame. */
        const val SETTLE_MILLIS = 100L
    }
}
