package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusBarTest {
    private fun bar(config: IntervalConfig, elapsedMillis: Long = 0): StatusBar? =
        StatusBar.of(WorkoutOutline.of(WorkoutEngine.tick(WorkoutEngine.start(config).state, elapsedMillis).state))

    @Test
    fun `a workout that fits shows each interval in its phase`() {
        val bar = bar(IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 15, rounds = 3), 12_000)!!
        assertEquals(
            listOf(
                BarSegment(10, Phase.Prep),
                BarSegment(30, Phase.Work),
                BarSegment(15, Phase.Rest),
                BarSegment(30, Phase.Work),
                BarSegment(15, Phase.Rest),
                BarSegment(30, Phase.Work),
            ),
            bar.segments,
        )
        assertEquals(12, bar.progress)
    }

    @Test
    fun `ten intervals are drawn one by one`() {
        // Prep, five work and four rests.
        val bar = bar(IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 5))!!
        assertEquals(StatusBar.MAX_SEGMENTS, bar.segments.size)
    }

    @Test
    fun `eleven intervals are one segment in the notification's colour`() {
        // Prep, six work and five rests: past what the system draws apart.
        val bar = bar(IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 6))!!
        assertEquals(listOf(BarSegment(10 + 6 * 20 + 5 * 10, null)), bar.segments)
    }

    @Test
    fun `each round after the first is marked when four or fewer`() {
        val bar = bar(IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 5))!!
        assertEquals(listOf(40, 70, 100, 130), bar.points)
    }

    @Test
    fun `more rounds than points are not marked`() {
        val bar = bar(IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 6))!!
        assertEquals(emptyList(), bar.points)
    }

    @Test
    fun `nothing to draw without a workout`() {
        assertNull(StatusBar.of(WorkoutOutline.Empty))
    }
}
