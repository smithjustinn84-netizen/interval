package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.Block
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
    fun `a workout of sets is a segment per set and per rest between them`() {
        // Prep, then 4 sets of 8 × 20/10 with a minute between: sixty-odd intervals, eight segments.
        val tabatas = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)
        val bar = bar(tabatas.copy(sets = 4, setRestSeconds = 60))!!
        val set = 8 * 20 + 7 * 10
        assertEquals(
            listOf(BarSegment(10, Phase.Prep)) +
                List(3) { listOf(BarSegment(set, Phase.Work), BarSegment(60, Phase.SetRest)) }.flatten() +
                BarSegment(set, Phase.Work),
            bar.segments,
        )
        assertEquals(listOf(10 + set + 60, 10 + 2 * (set + 60), 10 + 3 * (set + 60)), bar.points)
    }

    @Test
    fun `sets with no rest of their own keep the ordinary rest in the set before`() {
        val bar = bar(IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 2, sets = 2))!!
        assertEquals(
            listOf(BarSegment(20 + 10 + 20 + 10, Phase.Work), BarSegment(20 + 10 + 20, Phase.Work)),
            bar.segments,
        )
        assertEquals(listOf(60), bar.points)
    }

    @Test
    fun `nothing to draw without a workout`() {
        assertNull(StatusBar.of(WorkoutOutline.Empty))
    }

    @Test
    fun `a sequence is drawn a segment per block with the rest between them`() {
        val config = IntervalConfig(
            prepSeconds = 10,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 4,
            then = listOf(Block(workSeconds = 40, restSeconds = 20, rounds = 3)),
            blockRestSeconds = 60,
        )
        val bar = bar(config)!!
        assertEquals(
            listOf(
                BarSegment(10, Phase.Prep),
                BarSegment(4 * 20 + 3 * 10, Phase.Work),
                BarSegment(60, Phase.SetRest),
                BarSegment(3 * 40 + 2 * 20, Phase.Work),
            ),
            bar.segments,
        )
        // The second block begins after get ready, the first block and the rest.
        assertEquals(listOf(10 + 110 + 60), bar.points)
    }
}
