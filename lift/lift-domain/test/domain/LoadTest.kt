package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadTest {
    private val owned = listOf(24.0, 12.0, 16.0)

    private fun Load.kgStepped(step: Int, bar: Double? = null) = stepped(step, owned, bar, offered = emptyList())?.kg

    @Test
    fun `a bell steps to the next one owned and stops at either end`() {
        assertEquals(24.0, Load.Bell(16.0).kgStepped(1))
        assertEquals(12.0, Load.Bell(16.0).kgStepped(-1))
        assertNull(Load.Bell(24.0).kgStepped(1))
        assertNull(Load.Bell(12.0).kgStepped(-1))
    }

    @Test
    fun `a weight between two bells goes to the next one that way`() {
        assertEquals(16.0, Load.Bell(14.0).kgStepped(1))
        assertEquals(12.0, Load.Bell(14.0).kgStepped(-1))
    }

    @Test
    fun `the bar steps by its smallest plates and never below the empty bar`() {
        assertEquals(62.5, Load.Bar(60.0).kgStepped(1, bar = 20.0))
        assertEquals(57.5, Load.Bar(60.0).kgStepped(-1, bar = 20.0))
        assertNull(Load.Bar(20.0).kgStepped(-1, bar = 20.0))
        assertNull(Load.Bar(MAX_BAR_KG).kgStepped(1, bar = 20.0))
    }

    @Test
    fun `weight added to bodyweight goes from none to the lightest bell and back`() {
        assertEquals(12.0, Load.Bodyweight(0.0).kgStepped(1))
        assertEquals(0.0, Load.Bodyweight(12.0).kgStepped(-1))
        assertNull(Load.Bodyweight(0.0).kgStepped(-1))
    }

    @Test
    fun `a band steps along the bands offered and the mat never steps`() {
        val offered = bands(emptyList())
        assertEquals("Heavy", steppedBand(Band.Default, 1, offered)?.id)
        assertEquals("Light", steppedBand(Band.Default, -1, offered)?.id)
        assertNull(steppedBand(offered.last(), 1, offered))
        assertNull(steppedBand(null, 1, offered))
        assertEquals(Load.Banded(offered[3]), Load.Banded(Band.Default).stepped(1, owned, bar = null, offered))
        assertNull(Load.Banded(null).stepped(1, owned, bar = null, offered))
        assertNull(Load.Mat.stepped(1, owned, bar = null, offered))
    }
}
