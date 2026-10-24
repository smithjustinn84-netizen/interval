package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwakeHoldsTest {
    @Test
    fun `the display sleeps only once the last of overlapping holds leaves`() {
        val holds = AwakeHolds()
        assertTrue(holds.acquire())
        // A page arriving takes its hold before the one it replaces lets go of its own.
        assertFalse(holds.acquire())
        assertFalse(holds.release())
        assertTrue(holds.release())
    }

    @Test
    fun `a hold taken again after the last left keeps the display awake again`() {
        val holds = AwakeHolds()
        holds.acquire()
        holds.release()
        assertTrue(holds.acquire())
    }

    @Test
    fun `releasing more holds than were taken fails`() {
        assertFailsWith<IllegalStateException> { AwakeHolds().release() }
    }
}
