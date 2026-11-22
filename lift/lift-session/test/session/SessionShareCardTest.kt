package dev.justinsmith.lift.session

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionShareCardTest {
    @Test
    fun `tabular volume formats thousands cleanly without dropping decimals`() {
        assertEquals("0", formatTabularWeight(0.0))
        assertEquals("160", formatTabularWeight(160.0))
        assertEquals("1,440", formatTabularWeight(1440.0))
        assertEquals("12,345.5", formatTabularWeight(12345.5))
    }
}
