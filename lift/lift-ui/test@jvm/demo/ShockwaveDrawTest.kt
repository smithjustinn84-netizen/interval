@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Shockwave
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Core's shockwave as it is drawn, here where the UI harness is: core's own tests can't take it. */
class ShockwaveDrawTest {
    @Test
    fun `a shockwave is drawn through its run and leaves nothing behind`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag(GROUND)) {
                Shockwave(Color.White, Modifier.matchParentSize())
            }
        }
        mainClock.advanceTimeBy(Motion.SHOCKWAVE_MILLIS / 3L)
        val early = lit()
        assertTrue(early > 0, "Nothing drawn a third of the way through")
        mainClock.advanceTimeBy(Motion.SHOCKWAVE_MILLIS.toLong())
        assertEquals(0, lit())
    }

    @Test
    fun `a shockwave kept near stays inside its reach`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag(GROUND)) {
                Shockwave(Color.White, Modifier.matchParentSize(), reach = 40.dp)
            }
        }
        mainClock.advanceTimeBy(Motion.SHOCKWAVE_MILLIS * 2L / 3)
        val pixels = onNodeWithTag(GROUND).captureToImage().toPixelMap()
        assertTrue(lit() > 0)
        // Out at the edge of the box, well past 40dp from its middle, nothing is touched.
        assertEquals(Color.Black, pixels[2, pixels.height / 2])
        assertEquals(Color.Black, pixels[pixels.width / 2, 2])
    }

    @Test
    fun `a burst with a shock draws its shockwave ahead of its rings`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag(GROUND)) {
                Burst(listOf(Color.White), Modifier.matchParentSize(), shock = true)
            }
        }
        // The rings ease in slowly and the shockwave sets out fast: early on, the lit pixel farthest out is its.
        mainClock.advanceTimeBy(Motion.SHOCKWAVE_MILLIS / 3L)
        val struck = farthestLit()
        setThemedContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag(GROUND)) {
                Burst(listOf(Color.White), Modifier.matchParentSize())
            }
        }
        mainClock.advanceTimeBy(Motion.SHOCKWAVE_MILLIS / 3L)
        val plain = farthestLit()
        assertTrue(struck > plain, "With its shock the burst reaches $struck px, without it $plain")
    }

    private fun DesktopComposeUiTest.lit(): Int {
        val pixels = onNodeWithTag(GROUND).captureToImage().toPixelMap()
        var lit = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (pixels[x, y] != Color.Black) lit++
        return lit
    }

    /** How far right of the middle, along the middle row, the farthest pixel drawn on is. */
    private fun DesktopComposeUiTest.farthestLit(): Int {
        val pixels = onNodeWithTag(GROUND).captureToImage().toPixelMap()
        val middle = pixels.width / 2
        val row = pixels.height / 2
        return (pixels.width - 1 downTo middle).firstOrNull { pixels[it, row] != Color.Black }?.minus(middle) ?: 0
    }

    private companion object {
        const val GROUND = "ground"
    }
}
