package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.Block
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A sequence's blocks after its first, as every store keeps them: one JSON list, in a column, a preference or the
// saved workout alike. The first block is the config's own fields, stored as they always were.

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A [Block] as stored: primitives only, each read back into its range. */
@Serializable
internal class StoredBlock(
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val sets: Int = 1,
    val setRestSeconds: Int = 0,
    val workStepSeconds: Int = 0,
) {
    fun toDomain() = Block.sanitized(workSeconds, restSeconds, rounds, sets, setRestSeconds, workStepSeconds)
}

internal fun Block.stored() = StoredBlock(workSeconds, restSeconds, rounds, sets, setRestSeconds, workStepSeconds)

/** [blocks] as the text a column or preference holds: "[]" for none. */
internal fun encodeBlocks(blocks: List<Block>): String = json.encodeToString(blocks.map { it.stored() })

/** The blocks [text] holds; none when there is none or it can't be read: the workout still reads, as one block. */
internal fun decodeBlocks(text: String?): List<Block> {
    if (text.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<StoredBlock>>(text).map { it.toDomain() } }
        .onFailure { Logger.withTag("Blocks").w(it) { "Couldn't read a sequence's blocks" } }
        .getOrDefault(emptyList())
}
