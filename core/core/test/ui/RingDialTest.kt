package dev.justinsmith.interval.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingDialTest {
    @Test
    fun `a dial of a minute or less has a tick a second and a long one every five`() {
        assertEquals(DialTicks(count = 60f, majorEvery = 5), dialTicks(60))
        assertEquals(DialTicks(count = 20f, majorEvery = 5), dialTicks(20))
    }

    @Test
    fun `a longer dial steps up until it has no more than sixty ticks`() {
        assertEquals(DialTicks(count = 18f, majorEvery = 6), dialTicks(90))
        assertEquals(DialTicks(count = 60f, majorEvery = 6), dialTicks(300))
        assertEquals(DialTicks(count = 40f, majorEvery = 4), dialTicks(600))
        assertEquals(DialTicks(count = 20f, majorEvery = 5), dialTicks(1200))
    }

    @Test
    fun `a step that does not divide the interval leaves a part tick`() {
        assertEquals(DialTicks(count = 12.4f, majorEvery = 6), dialTicks(62))
    }

    @Test
    fun `an interval of no length has no ticks`() {
        assertNull(dialTicks(0))
    }

    @Test
    fun `a draining ring has its share left and a filling one the rest`() {
        assertEquals(7.5f, RingDial(seconds = 30, draining = true).secondsLeft(0.25f))
        assertEquals(22.5f, RingDial(seconds = 30).secondsLeft(0.25f))
    }

    @Test
    fun `the last ten seconds are urgent or a third of a shorter interval`() {
        assertEquals(10f, RingDial(seconds = 60).urgentSeconds)
        assertEquals(5f, RingDial(seconds = 15).urgentSeconds)
    }

    @Test
    fun `a short interval is urgent for its countdown at least and never longer than it lasts`() {
        assertEquals(3f, RingDial(seconds = 6).urgentSeconds)
        assertEquals(2f, RingDial(seconds = 2).urgentSeconds)
    }

    @Test
    fun `the heat is nothing until the urgent seconds and full as the interval ends`() {
        val dial = RingDial(seconds = 60, draining = true)
        assertEquals(0f, dial.heat(progress = 0.5f))
        assertEquals(0f, dial.heat(progress = 10f / 60))
        assertEquals(0.5f, dial.heat(progress = 5f / 60), absoluteTolerance = 0.001f)
        assertEquals(1f, dial.heat(progress = 0f))
    }

    @Test
    fun `the heat only grows as the seconds go`() {
        val heats = (100 downTo 0).map { heatAt(left = it / 10f, urgent = 10f) }
        assertTrue(heats.zipWithNext().all { (earlier, later) -> later >= earlier })
    }

    @Test
    fun `a beat lands as a second arrives and is gone before the next`() {
        assertEquals(1f, beatAt(left = 3f, urgent = 10f))
        assertEquals(0.5f, beatAt(left = 2.85f, urgent = 10f), absoluteTolerance = 0.01f)
        assertEquals(0f, beatAt(left = 2.5f, urgent = 10f))
    }

    @Test
    fun `before the countdown the beat lands on every other second`() {
        assertEquals(1f, beatAt(left = 8f, urgent = 10f))
        assertEquals(0f, beatAt(left = 7f, urgent = 10f))
        assertEquals(1f, beatAt(left = 6f, urgent = 10f))
        assertEquals(1f, beatAt(left = 4f, urgent = 10f))
    }

    @Test
    fun `nothing beats outside the urgent seconds or once the interval is over`() {
        assertEquals(0f, beatAt(left = 12f, urgent = 10f))
        assertEquals(0f, beatAt(left = 0f, urgent = 10f))
    }
}
