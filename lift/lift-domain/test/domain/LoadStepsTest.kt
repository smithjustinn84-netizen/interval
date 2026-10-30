package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadStepsTest {
    private val owned = listOf(24.0, 12.0, 16.0)

    @Test
    fun `a bell steps to the next one owned and stops at either end`() {
        assertEquals(24.0, steppedKg(Equipment.Kettlebell, 16.0, 1, owned, bar = null))
        assertEquals(12.0, steppedKg(Equipment.Kettlebell, 16.0, -1, owned, bar = null))
        assertNull(steppedKg(Equipment.Kettlebell, 24.0, 1, owned, bar = null))
        assertNull(steppedKg(Equipment.Kettlebell, 12.0, -1, owned, bar = null))
    }

    @Test
    fun `a weight between two bells goes to the next one that way`() {
        assertEquals(16.0, steppedKg(Equipment.Kettlebell, 14.0, 1, owned, bar = null))
        assertEquals(12.0, steppedKg(Equipment.Kettlebell, 14.0, -1, owned, bar = null))
    }

    @Test
    fun `the bar steps by its smallest plates and never below the empty bar`() {
        assertEquals(62.5, steppedKg(Equipment.Barbell, 60.0, 1, owned, bar = 20.0))
        assertEquals(57.5, steppedKg(Equipment.Barbell, 60.0, -1, owned, bar = 20.0))
        assertNull(steppedKg(Equipment.Barbell, 20.0, -1, owned, bar = 20.0))
        assertNull(steppedKg(Equipment.Barbell, MAX_BAR_KG, 1, owned, bar = 20.0))
    }

    @Test
    fun `weight added to bodyweight goes from none to the lightest bell and back`() {
        assertEquals(12.0, steppedKg(Equipment.Bodyweight, 0.0, 1, owned, bar = null))
        assertEquals(0.0, steppedKg(Equipment.Bodyweight, 12.0, -1, owned, bar = null))
        assertNull(steppedKg(Equipment.Bodyweight, 0.0, -1, owned, bar = null))
    }

    @Test
    fun `a band steps along the bands offered and the mat never steps`() {
        val offered = bands(emptyList())
        assertEquals("Heavy", steppedBand(Band.Default, 1, offered)?.id)
        assertEquals("Light", steppedBand(Band.Default, -1, offered)?.id)
        assertNull(steppedBand(offered.last(), 1, offered))
        assertNull(steppedBand(null, 1, offered))
        assertNull(steppedKg(Equipment.Mat, 0.0, 1, owned, bar = null))
        assertNull(steppedKg(Equipment.Band, 0.0, 1, owned, bar = null))
    }
}
