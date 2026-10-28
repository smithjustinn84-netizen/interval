package dev.justinsmith.bells.data

import co.touchlab.kermit.Logger
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.PlanBlock
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.RepRange
import dev.justinsmith.bells.domain.SessionPlan
import dev.justinsmith.bells.domain.takesKg
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A block as [CustomProgramEntity.days] stores it: the lift and kind by name, so a rename is a migration, and
 * its load if it names one.
 */
@Serializable
internal data class StoredBlock(
    val exercise: String,
    val kind: String,
    val rounds: Int,
    val reps: Int = 0,
    val rungs: List<Int> = emptyList(),
    val double: Boolean = false,
    val kg: Double? = null,
    val band: String? = null,
    val rangeBottom: Int? = null,
    val rangeTop: Int? = null,
)

private val json = Json { ignoreUnknownKeys = true }
private val log = Logger.withTag("StoredPrograms")

/** [days] as [CustomProgramEntity.days] holds them; public so a test can store a program as the app does. */
fun encodeDays(days: List<List<Block>>): String = json.encodeToString(
    days.map { day ->
        day.map { it.toStored() }
    },
)

/**
 * Reads an in-progress draft's days encoded with [encodeDays], keeping empty days intact so an unfinished
 * editor draft survives process recreation; null if [encoded] cannot be read.
 */
fun decodeDraftDays(encoded: String, made: List<CustomExercise> = emptyList()): List<List<Block>>? {
    val stored = try {
        json.decodeFromString<List<List<StoredBlock>>>(encoded)
    } catch (e: IllegalArgumentException) {
        log.w(e) { "Couldn't read a draft's days" }
        return null
    }
    if (stored.isEmpty()) return null
    return stored.map { day -> day.mapNotNull { it.toBlock(made) } }
}

internal fun Block.toStored() =
    StoredBlock(exercise.key, kind.name, rounds, reps, rungs, double, kg, band, range?.bottom, range?.top)

/**
 * The program this row holds, its blocks naming built-ins or [made]. A block of a lift or kind this build
 * doesn't know is left out, then a day left with none; a program left with no days, or whose days can't be
 * read, is null.
 */
internal fun CustomProgramEntity.toProgram(made: List<CustomExercise> = emptyList()): Program? {
    val stored = try {
        json.decodeFromString<List<List<StoredBlock>>>(days)
    } catch (e: IllegalArgumentException) {
        // A SerializationException included: JSON this build can't read.
        log.w(e) { "Couldn't read a stored program's days" }
        return null
    }
    val blocks = stored.map { day -> day.mapNotNull { it.toBlock(made) } }.filter { it.isNotEmpty() }
    if (blocks.isEmpty()) return null
    return Program(ProgramKey.Custom(uid), blocks, daysPerWeek.coerceAtLeast(1), weeks.coerceAtLeast(1), name)
}

/**
 * The block this names, of a built-in or one of [made]; a load that doesn't suit the lift is dropped, and a
 * range that doesn't climb.
 */
internal fun StoredBlock.toBlock(made: List<CustomExercise>): Block? {
    val lift = Exercise.parse(exercise, made) ?: return null
    val kind = BlockKind.entries.firstOrNull { it.name == kind } ?: return null
    if (rounds < 1 || (kind == BlockKind.Ladder && rungs.isEmpty())) return null
    return Block(
        lift,
        rounds,
        reps,
        kind,
        rungs,
        lift.doubled(double),
        kg = kg?.takeIf { lift.equipment.takesKg && it >= 0 },
        band = band?.takeIf { lift.equipment == Equipment.Band },
        range = if (rangeBottom != null && rangeTop != null && rangeBottom in 1 until rangeTop) {
            RepRange(rangeBottom, rangeTop)
        } else {
            null
        },
    )
}

/** One block of a session's plan as [SessionEntity.plan] stores it, in [PlanBlock]'s terms. */
@Serializable
internal data class StoredPlanBlock(
    val block: StoredBlock,
    val kept: Int? = null,
    val before: StoredBlock? = null,
    val beforeDone: Int = 0,
)

/** [plan] as [SessionEntity.plan] holds it. */
internal fun encodePlan(plan: SessionPlan): String = json.encodeToString(
    plan.blocks.map { StoredPlanBlock(it.block.toStored(), it.kept, it.before?.toStored(), it.beforeDone) },
)

/**
 * The plan [stored] holds, its blocks naming built-ins or [made]; null, the day as programmed, if any of it
 * can't be read.
 */
internal fun decodePlan(stored: String, made: List<CustomExercise>): SessionPlan? {
    val blocks = try {
        json.decodeFromString<List<StoredPlanBlock>>(stored)
    } catch (e: IllegalArgumentException) {
        log.w(e) { "Couldn't read a stored day's plan" }
        return null
    }
    val plan = blocks.map { entry ->
        val block = entry.block.toBlock(made) ?: return null
        val before = entry.before?.let { it.toBlock(made) ?: return null }
        PlanBlock(block, entry.kept?.coerceAtLeast(0), before, entry.beforeDone.coerceAtLeast(0))
    }
    return SessionPlan(plan).takeIf { plan.isNotEmpty() }
}
