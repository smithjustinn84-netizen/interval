package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A block as [CustomProgramEntity.days] stores it: the lift and kind by name, so a rename is a migration. */
@Serializable
internal data class StoredBlock(
    val exercise: String,
    val kind: String,
    val rounds: Int,
    val reps: Int = 0,
    val rungs: List<Int> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/** [days] as [CustomProgramEntity.days] holds them. */
internal fun encodeDays(days: List<List<Block>>): String = json.encodeToString(
    days.map { day -> day.map { StoredBlock(it.exercise.name, it.kind.name, it.rounds, it.reps, it.rungs) } },
)

/**
 * The program this row holds. A block of a lift or kind this build doesn't know is left out, then a day
 * left with none; a program left with no days, or whose days can't be read, is null.
 */
internal fun CustomProgramEntity.toProgram(): Program? {
    val stored = try {
        json.decodeFromString<List<List<StoredBlock>>>(days)
    } catch (_: IllegalArgumentException) {
        // A SerializationException included: JSON this build can't read.
        return null
    }
    val blocks = stored.map { day -> day.mapNotNull { it.toBlock() } }.filter { it.isNotEmpty() }
    if (blocks.isEmpty()) return null
    return Program(ProgramKey.Custom(uid), blocks, daysPerWeek.coerceAtLeast(1), weeks.coerceAtLeast(1), name)
}

private fun StoredBlock.toBlock(): Block? {
    val lift = Exercise.named(exercise) ?: return null
    val kind = BlockKind.entries.firstOrNull { it.name == kind } ?: return null
    if (rounds < 1 || (kind == BlockKind.Ladder && rungs.isEmpty())) return null
    return Block(lift, rounds, reps, kind, rungs)
}
