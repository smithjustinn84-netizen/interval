package dev.justinsmith.interval.export

import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move

/** A flow from a file, its moves in their ranges: one with none, or too many, or no name, makes the file unreadable. */
internal fun FlowExport.toDomain(): Flow {
    require(name.isNotBlank() && moves.size in 1..Flow.MAX_MOVES) { "flow" }
    return Flow(
        key = "import",
        name = name.trim().take(Flow.NAME_MAX_LENGTH),
        use = FlowUse.entries.firstOrNull { it.name == use } ?: throw IllegalArgumentException("use"),
        moves = moves.map {
            require(it.name.isNotBlank() && it.seconds in Move.SECONDS_RANGE) { "move" }
            Move(
                it.name.take(Move.NAME_MAX_LENGTH),
                it.seconds,
                it.cue.take(Move.CUE_MAX_LENGTH),
                it.sided,
                it.held,
                it.exercise?.ifBlank { null },
            )
        },
        note = note?.take(Flow.NOTE_MAX_LENGTH)?.ifBlank { null },
    )
}
