package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.ownFlowKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A flow's moves as the database keeps them: one JSON list in a column, each move read back into its ranges.

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A [Move] as stored: primitives only. */
@Serializable
internal class StoredMove(
    val name: String,
    val seconds: Int,
    val cue: String = "",
    val sided: Boolean = false,
    val held: Boolean = true,
) {
    fun toDomain() = Move(
        name.take(Move.NAME_MAX_LENGTH),
        seconds.coerceIn(Move.SECONDS_RANGE),
        cue.take(Move.CUE_MAX_LENGTH),
        sided,
        held,
    )
}

internal fun Move.stored() = StoredMove(name, seconds, cue, sided, held)

/** [moves] as the text a column holds. */
internal fun encodeMoves(moves: List<Move>): String = json.encodeToString(moves.map { it.stored() })

/** The moves [text] holds; none when it can't be read, which leaves the flow out. */
internal fun decodeMoves(text: String): List<Move> =
    runCatching { json.decodeFromString<List<StoredMove>>(text).map { it.toDomain() } }
        .onFailure { Logger.withTag("Flows").w(it) { "Couldn't read a flow's moves" } }
        .getOrDefault(emptyList())

/** The flow this row is; null for one with no move it can read, or too many, which is left out. */
internal fun FlowEntity.toDomain(): Flow? {
    val moves = decodeMoves(moves).take(Flow.MAX_MOVES)
    if (moves.isEmpty() || name.isBlank()) return null
    return Flow(
        key = ownFlowKey(id),
        name = name.take(Flow.NAME_MAX_LENGTH),
        use = FlowUse.entries.firstOrNull { it.name == use } ?: FlowUse.After,
        moves = moves,
        note = note?.take(Flow.NOTE_MAX_LENGTH)?.ifBlank { null },
        id = id,
    )
}

internal fun Flow.toEntity() = FlowEntity(
    id = id ?: 0,
    name = name,
    use = use.name,
    note = note,
    moves = encodeMoves(moves),
)
