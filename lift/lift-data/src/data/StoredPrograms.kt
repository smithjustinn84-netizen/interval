package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BlockKind
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PlanBlock
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RepRange
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.domain.band
import dev.justinsmith.lift.domain.bandOf
import dev.justinsmith.lift.domain.weighedKg
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A block as [CustomProgramEntity.days] stores it: the lift and kind by name, so a rename is a migration, and
 * its load if it names one, as its parts: [kg] where it is weighed, [band] by a band's id.
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
    val rest: Int? = null,
    /** The block it goes back to at the next bell ([Block.origin]); none for one never stepped at its bell. */
    val origin: StoredBlock? = null,
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
 * editor draft survives process recreation; null if [encoded] cannot be read. Its blocks name built-ins or
 * [made], and their bands [bands].
 */
fun decodeDraftDays(
    encoded: String,
    made: List<CustomExercise> = emptyList(),
    bands: List<Band> = emptyList(),
): List<List<Block>>? {
    val stored = try {
        json.decodeFromString<List<List<StoredBlock>>>(encoded)
    } catch (e: IllegalArgumentException) {
        log.w(e) { "Couldn't read a draft's days" }
        return null
    }
    if (stored.isEmpty()) return null
    return stored.map { day -> day.mapNotNull { it.toBlock(made, bands) } }
}

internal fun Block.toStored(): StoredBlock = StoredBlock(
    exercise.key,
    kind.name,
    rounds,
    reps,
    rungs,
    double,
    load?.weighedKg,
    load?.band?.id,
    range?.bottom,
    range?.top,
    rest,
    origin?.toStored(),
)

/**
 * The program this row holds, its blocks naming built-ins or [made], and their bands [bands]. A block of a lift
 * or kind this build doesn't know is left out, then a day left with none; a program left with no days, or whose
 * days can't be read, is null.
 */
internal fun CustomProgramEntity.toProgram(
    made: List<CustomExercise> = emptyList(),
    bands: List<Band> = emptyList(),
): Program? {
    val stored = try {
        json.decodeFromString<List<List<StoredBlock>>>(days)
    } catch (e: IllegalArgumentException) {
        // A SerializationException included: JSON this build can't read.
        log.w(e) { "Couldn't read a stored program's days" }
        return null
    }
    val blocks = stored.map { day -> day.mapNotNull { it.toBlock(made, bands) } }.filter { it.isNotEmpty() }
    if (blocks.isEmpty()) return null
    return Program(ProgramKey.Custom(uid), blocks, daysPerWeek.coerceAtLeast(1), weeks.coerceAtLeast(1), name)
}

/**
 * The block this names, of a built-in or one of [made], its band one of [bands] by id; a load that doesn't suit
 * the lift is dropped, and a range that doesn't climb.
 */
internal fun StoredBlock.toBlock(made: List<CustomExercise>, bands: List<Band>): Block? {
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
        load = Load.named(
            lift.equipment,
            kg?.takeIf {
                it >= 0
            },
            lift.doubled(double),
            band?.let { bandOf(it, bands) },
        ),
        range = if (rangeBottom != null && rangeTop != null && rangeBottom in 1 until rangeTop) {
            RepRange(rangeBottom, rangeTop)
        } else {
            null
        },
        rest = rest?.takeIf { it in RestClock.MIN_SECONDS..RestClock.MAX_SECONDS },
        // An origin of a lift this build doesn't know is dropped: the block then goes on as it stands.
        origin = origin?.toBlock(made, bands)?.copy(origin = null),
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
 * The plan [stored] holds, its blocks naming built-ins or [made], and their bands [bands]; null, the day as
 * programmed, if any of it can't be read.
 */
internal fun decodePlan(stored: String, made: List<CustomExercise>, bands: List<Band>): SessionPlan? {
    val blocks = try {
        json.decodeFromString<List<StoredPlanBlock>>(stored)
    } catch (e: IllegalArgumentException) {
        log.w(e) { "Couldn't read a stored day's plan" }
        return null
    }
    val plan = blocks.map { entry ->
        val block = entry.block.toBlock(made, bands) ?: return null
        val before = entry.before?.let { it.toBlock(made, bands) ?: return null }
        PlanBlock(block, entry.kept?.coerceAtLeast(0), before, entry.beforeDone.coerceAtLeast(0))
    }
    return SessionPlan(plan).takeIf { plan.isNotEmpty() }
}
