package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CapRoundsTest {
    private val cap = IntervalConfig(workSeconds = 600, restSeconds = 0, rounds = 1, capped = true)

    private fun entry(config: IntervalConfig, rounds: List<CapRound>?) =
        HistoryEntry(0, config, 600, endedEarly = false, workActiveSeconds = 600, capRounds = rounds)

    @Test
    fun `a cap's splits run from its start`() {
        val score = listOf(CapRound(0, 70_000), CapRound(0, 150_000), CapRound(0, 210_000)).scores(cap).single()
        assertEquals(listOf(70_000L, 80_000L, 60_000L), score.splitsMillis)
        assertEquals(3, score.rounds)
        assertEquals(70_000L, score.paceMillis)
        assertEquals(60_000L, score.fastestMillis)
        assertEquals(80_000L, score.slowestMillis)
    }

    @Test
    fun `a cap with no round has no pace`() {
        val score = emptyList<CapRound>().scores(cap).single()
        assertEquals(0, score.rounds)
        assertNull(score.paceMillis)
    }

    @Test
    fun `each cap of a sequence keeps its own rounds`() {
        val two = cap.withBlocks(listOf(Block.capOf(600, 60), IntervalConfig().blocks.first(), Block.capOf(300)))
        val scores = listOf(CapRound(0, 50_000), CapRound(2, 40_000), CapRound(2, 90_000)).scores(two)
        assertEquals(listOf(0 to 1, 2 to 2), scores.map { it.block to it.rounds })
        assertEquals(listOf(40_000L, 50_000L), scores[1].splitsMillis)
    }

    @Test
    fun `undoing takes back the block's last round only`() {
        val rounds = listOf(CapRound(0, 10), CapRound(1, 20), CapRound(0, 30), CapRound(1, 40))
        assertEquals(listOf(CapRound(0, 10), CapRound(1, 20), CapRound(1, 40)), rounds.withoutLastIn(0))
        assertEquals(rounds, rounds.withoutLastIn(2))
    }

    @Test
    fun `the best is the most rounds of the same shape counted`() {
        val history = listOf(
            entry(cap, List(12) { CapRound(0, it * 1_000L) }),
            entry(cap, List(14) { CapRound(0, it * 1_000L) }),
            entry(cap.copy(workSeconds = 1200), List(20) { CapRound(0, it * 1_000L) }),
            entry(cap, null),
        )
        assertEquals(14, bestRounds(cap, 0, history))
    }

    @Test
    fun `a cap never counted has no best`() {
        assertNull(bestRounds(cap, 0, listOf(entry(cap, null))))
        assertEquals(0, bestRounds(cap, 0, listOf(entry(cap, emptyList()))))
    }
}
