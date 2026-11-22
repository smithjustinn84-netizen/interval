package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiltTest {
    private val card = IntSize(200, 100)

    @Test
    fun `a touch at a card's middle leans it no way`() {
        assertEquals(Offset.Zero, leanAt(Offset(100f, 50f), card))
    }

    @Test
    fun `a touch at a corner leans it fully that way`() {
        assertEquals(Offset(-1f, -1f), leanAt(Offset.Zero, card))
        assertEquals(Offset(1f, 1f), leanAt(Offset(200f, 100f), card))
        assertEquals(Offset(0.5f, -0.5f), leanAt(Offset(150f, 25f), card))
    }

    @Test
    fun `a finger dragged off the card leans it no further than its edge`() {
        assertEquals(Offset(1f, -1f), leanAt(Offset(900f, -40f), card))
    }

    @Test
    fun `a card not yet measured doesn't lean`() {
        assertEquals(Offset.Zero, leanAt(Offset(10f, 10f), IntSize.Zero))
    }

    @Test
    fun `a squircle meets its box at the sides and fills its corners past a circle's`() {
        assertEquals(1f, squircleReach(1f))
        assertEquals(-1f, squircleReach(-1f))
        assertEquals(0f, squircleReach(0f))
        val diagonal = 1f / sqrt(2f)
        assertTrue(squircleReach(diagonal) > diagonal)
        assertEquals(-squircleReach(diagonal), squircleReach(-diagonal))
    }
}
