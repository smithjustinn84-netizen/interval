package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A shockwave's shape over its run, and where in a celebration it sets out. */
class ShockwaveTest {
    private val steps = (0..STEPS).map { it / STEPS.toFloat() }

    @Test
    fun `the front sets out from the point and stops at its reach`() {
        assertEquals(0f, shockwaveSpread(0f))
        assertEquals(1f, shockwaveSpread(1f))
        steps.zipWithNext { earlier, later -> assertTrue(shockwaveSpread(later) > shockwaveSpread(earlier)) }
    }

    @Test
    fun `the front is fast first and slows as a blast does`() {
        assertTrue(shockwaveSpread(THIRD) > 2 * THIRD, "Most of the way in the first third")
        val gains = steps.zipWithNext { earlier, later -> shockwaveSpread(later) - shockwaveSpread(earlier) }
        gains.zipWithNext { earlier, later -> assertTrue(later < earlier, "Each step gains less than the last") }
    }

    @Test
    fun `it is nothing as it sets out and nothing once spent and strongest early`() {
        assertEquals(0f, shockwaveStrength(0f))
        assertEquals(0f, shockwaveStrength(1f))
        val strongest = steps.maxBy { shockwaveStrength(it) }
        assertTrue(strongest > 0f && strongest < THIRD, "Strongest at $strongest")
        assertTrue(steps.all { shockwaveStrength(it) in 0f..1f })
    }

    @Test
    fun `it holds most of its strength while the front is still travelling`() {
        // Half way through, the front is most of the way out: it must still be seen there.
        assertTrue(shockwaveSpread(0.5f) > 0.8f)
        assertTrue(shockwaveStrength(0.5f) > 0.7f)
    }

    @Test
    fun `the front thins as it goes and never to nothing`() {
        assertEquals(1f, shockwaveWidth(0f))
        steps.zipWithNext { earlier, later -> assertTrue(shockwaveWidth(later) < shockwaveWidth(earlier)) }
        assertTrue(shockwaveWidth(1f) > 0f)
    }

    @Test
    fun `a celebration's shockwave sets out as the figure tops out and is spent inside the hold`() {
        assertTrue(Celebration.shockAt(0f) < 0f)
        assertTrue(Celebration.shockAt(Celebration.TOP - 0.01f) < 0f)
        assertEquals(0f, Celebration.shockAt(Celebration.TOP))
        val spentAt = Celebration.TOP + Motion.SHOCKWAVE_MILLIS / Celebration.MILLIS.toFloat()
        assertTrue(Celebration.shockAt(spentAt + 0.001f) > 1f)
        assertTrue(spentAt < HOLD_ENDS, "The hold ends at $HOLD_ENDS and the shockwave at $spentAt")
    }

    @Test
    fun `a shockwave is shorter than the burst it leads`() {
        assertTrue(Motion.SHOCKWAVE_MILLIS < Motion.BURST_MILLIS)
    }

    @Test
    fun `the shader takes every uniform it is drawn with where there are shaders`() {
        val program = shaderProgram(SHOCKWAVE_SKSL) ?: return
        program.set("uCenter", 120f, 40f)
        program.set("uRadius", 64f)
        program.set("uHalfWidth", 1.5f)
        program.set("uWake", 36f)
        program.set("uStrength", 0.8f)
        program.set("uGain", 0.7f)
        program.set("uColor", Color(0xFFFF7A43))
        program.brush()
    }

    private companion object {
        const val STEPS = 20
        const val THIRD = 1 / 3f

        /** The gesture holds its top from [Celebration.TOP] for 0.4 of its run. */
        const val HOLD_ENDS = 0.7f
    }
}
