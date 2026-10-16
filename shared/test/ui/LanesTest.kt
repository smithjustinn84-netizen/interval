package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanesTest {
    private val width = 390f
    private val height = 844f
    private val centre = Offset(-700f, 900f)

    /** A degree of arc, plus the sampling's own degree. */
    private val tolerance = 2 * PI.toFloat() / 180

    @Test
    fun visibleArcRunsFromTheStartEdgeDownToTheBottom() {
        val radius = 900f
        val arc = assertNotNull(visibleArc(centre, radius, width, height, margin = 0f))
        // In at x = 0, above the centre; out at y = height, just above the centre's height.
        val entry = -acos((0f - centre.x) / radius)
        val exit = asin((height - centre.y) / radius)
        assertEquals(entry, arc.start, tolerance)
        assertEquals(exit, arc.endInclusive, tolerance)
    }

    @Test
    fun aLaneThatMissesTheBoxHasNoVisibleArc() {
        assertNull(visibleArc(centre, radius = 100f, width, height, margin = 0f))
    }

    @Test
    fun theMarginWidensTheArc() {
        val tight = assertNotNull(visibleArc(centre, 900f, width, height, margin = 0f))
        val wide = assertNotNull(visibleArc(centre, 900f, width, height, margin = 80f))
        assertTrue(wide.start < tight.start && wide.endInclusive > tight.endInclusive)
    }

    @Test
    fun aCometStartsAtTheArcsStartAndEndsATailPastItsEnd() {
        val arc = 1f..2f
        assertEquals(1f, cometHead(arc, tail = 0.5f, progress = 0f))
        assertEquals(2.5f, cometHead(arc, tail = 0.5f, progress = 1f))
        assertEquals(1.75f, cometHead(arc, tail = 0.5f, progress = 0.5f))
    }

    @Test
    fun laneLinesAreQuieterThanALine() {
        listOf(lightPalette, darkPalette).forEach { palette ->
            val page = palette.background.luminance()
            val lane = abs(palette.laneLine.luminance() - page)
            val line = abs(palette.line.luminance() - page)
            assertTrue(lane > 0f && lane < line, "lane lines must show, but more quietly than a line")
        }
    }

    @Test
    fun laneFillsSitBetweenThePageAndACard() {
        listOf(lightPalette, darkPalette).forEach { palette ->
            val fill = palette.laneFill.luminance()
            val low = minOf(palette.background.luminance(), palette.surface.luminance())
            val high = maxOf(palette.background.luminance(), palette.surface.luminance())
            assertTrue(fill > low && fill < high)
        }
        val fill = PhaseColors.laneFill.luminance()
        assertTrue(fill > PhaseColors.ground.luminance() && fill < PhaseColors.control.luminance())
    }
}
