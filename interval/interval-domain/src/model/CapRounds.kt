package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.sameShape

/**
 * One round of a time cap done ([dev.justinsmith.interval.timer.Block.capped]): the cap's [block] (0-based) and how
 * far into it the round was done, [atMillis], pauses left out. Counted from the cap's start rather than kept as a
 * moment, so it reads the same after an export, which keeps no start.
 */
data class CapRound(val block: Int, val atMillis: Long) {
    init {
        require(block >= 0) { "Block $block" }
        require(atMillis >= 0) { "A round at $atMillis ms" }
    }
}

/**
 * The rounds one time cap held, as done: its [block] (0-based), its length, [capSeconds], and each round's time,
 * [splitsMillis], the first from the cap's start.
 */
data class CapScore(val block: Int, val capSeconds: Int, val splitsMillis: List<Long>) {
    val rounds: Int get() = splitsMillis.size

    /** The time a round took on average, null before the first. */
    val paceMillis: Long? get() = if (splitsMillis.isEmpty()) null else splitsMillis.sum() / splitsMillis.size

    /** The quickest round, null before the first. */
    val fastestMillis: Long? get() = splitsMillis.minOrNull()

    /** The slowest round, null before the first. */
    val slowestMillis: Long? get() = splitsMillis.maxOrNull()
}

/** The rounds done in [block] (0-based). */
fun List<CapRound>.roundsIn(block: Int): Int = count { it.block == block }

/** Each time cap of [config], in order, with the rounds these hold of it; none for a config with no cap. */
fun List<CapRound>.scores(config: IntervalConfig): List<CapScore> =
    config.blocks.mapIndexedNotNull { index, block ->
        if (!block.capped) return@mapIndexedNotNull null
        val ends = filter { it.block == index }.map { it.atMillis }.sorted()
        CapScore(index, block.workSeconds, ends.zip(listOf(0L) + ends) { end, start -> end - start })
    }

/** These rounds without the last done in [block] (0-based), as an undone tap leaves them. */
fun List<CapRound>.withoutLastIn(block: Int): List<CapRound> {
    val last = indexOfLast { it.block == block }
    return if (last < 0) this else filterIndexed { index, _ -> index != last }
}

/**
 * The most rounds done in [block] (0-based) of a workout of [config]'s shape among [history]. One whose rounds were
 * never counted (logged by hand, or from before caps were counted) is left out, so it is never a best of none;
 * null when none was counted.
 */
fun bestRounds(config: IntervalConfig, block: Int, history: List<HistoryEntry>): Int? =
    history.filter { it.config.sameShape(config) }.mapNotNull { it.capRounds?.roundsIn(block) }.maxOrNull()
