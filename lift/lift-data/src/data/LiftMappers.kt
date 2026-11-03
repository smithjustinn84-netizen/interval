package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.UnknownExercise
import dev.justinsmith.lift.domain.band
import dev.justinsmith.lift.domain.bandOf
import dev.justinsmith.lift.domain.builtInPrograms

// Lift's rows as the domain reads them, and the domain as a row stores it: what every repository of
// LiftRepository maps through. A program's blocks and a session's plan and timer are encoded in StoredPrograms.kt
// and StoredTimer.kt.

/**
 * A set, naming one of [made] or a built-in; one this build can't name reads as an [UnknownExercise], so it
 * is never lost. A set logged before version 7 was done with a kettlebell. One of a lift only done with two
 * bells is read as such, though logged before sets had the column.
 */
internal fun SetEntity.toDomain(made: List<CustomExercise>, bands: List<Band>): SetLog {
    val done = equipment?.let { e -> Equipment.entries.firstOrNull { it.name == e } } ?: Equipment.Kettlebell
    val lift = Exercise.parse(exercise, made)
        ?: UnknownExercise(exercise, done, if (seconds != null) Measure.Time else Measure.Reps)
    return SetLog.of(
        id,
        lift,
        bellKg,
        reps,
        side?.let { s -> Side.entries.firstOrNull { it.name == s } },
        loggedAtEpochMillis,
        lift.doubled(doubled),
        equipment = done,
        seconds = seconds,
        band = band?.let { bandOf(it, bands) },
    )
}

/** [SetLog] as the row of [sessionUid]: a new one with [id] 0, or one put back under its own. */
internal fun SetLog.toEntity(sessionUid: String, id: Long = 0): SetEntity = SetEntity(
    id,
    sessionUid,
    exercise.key,
    load.kg,
    reps,
    side?.name,
    loggedAtEpochMillis,
    double,
    equipment = load.equipment.name,
    seconds = seconds,
    band = load.band?.id,
)

internal fun SessionEntity.toDomain(sets: List<SetLog>, made: List<CustomExercise>, bands: List<Band>) =
    LiftSession(
        uid,
        startedAtEpochMillis,
        finishedAtEpochMillis,
        title,
        sets,
        programId?.let(ProgramKey::parse),
        plan?.let { decodePlan(it, made) },
        programDay,
        PerceivedEffort.of(effort),
        note,
        timer?.takeIf { finishedAtEpochMillis == null }?.let { decodeTimer(it, made, bands) },
        averageBpm,
        peakBpm,
    )

/** A finished session as an import stores it: as it was, but changed now. */
internal fun LiftSession.importRow(now: Long) = SessionEntity(
    uid = uid,
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    title = title,
    updatedAtEpochMillis = now,
    programId = program?.stored,
    programDay = day,
    effort = effort?.level,
    note = note,
    averageBpm = averageBpm,
    peakBpm = peakBpm,
)

/** An exercise the user added; one whose kinds this build doesn't know is left out. */
internal fun CustomExerciseEntity.toDomain(): CustomExercise? = CustomExercise(
    uid,
    name,
    equipment = Equipment.entries.firstOrNull { it.name == equipment } ?: return null,
    measure = Measure.entries.firstOrNull { it.name == measure } ?: return null,
    perSide = perSide,
    focus = Focus.entries.firstOrNull { it.name == focus } ?: return null,
    cues = cues,
    archived = archived,
)

internal fun CustomExercise.toEntity(updatedAtEpochMillis: Long) = CustomExerciseEntity(
    uid,
    name,
    equipment.name,
    measure.name,
    perSide,
    focus.name,
    cues,
    archived,
    updatedAtEpochMillis,
)

internal fun BandEntity.toDomain() =
    Band(id, name, BandColour.entries.firstOrNull { it.name == colour } ?: BandColour.Grey, position, archived)

internal fun Band.toEntity(updatedAtEpochMillis: Long) =
    BandEntity(id, name, colour.name, position, archived, updatedAtEpochMillis)

/** A program of the user's own as stored; a built-in has no row, so null. */
internal fun Program.importRow(now: Long): CustomProgramEntity? {
    val custom = key as? ProgramKey.Custom ?: return null
    return CustomProgramEntity(custom.uid, name.orEmpty(), daysPerWeek, weeks, encodeDays(days), now)
}

/** The program [stored] names: a built-in, or one of [made]; null for one gone or unknown. */
internal fun resolveProgram(stored: String, made: List<Program>): Program? = ProgramKey.parse(stored)?.let { key ->
    (builtInPrograms + made).firstOrNull { it.key == key }
}
