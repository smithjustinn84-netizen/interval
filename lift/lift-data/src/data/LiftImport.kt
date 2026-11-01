package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program

/**
 * What an import adds to Lift, as plain models: finished [sessions] with their sets, the user's [exercises],
 * [programs] (their own, by [dev.justinsmith.lift.domain.ProgramKey.Custom]) and [bands], the bells owned
 * ([bellsKg]). [barKg], [following] and [restSeconds] are set when not null, and left as they are when null.
 */
data class LiftImport(
    val sessions: List<LiftSession> = emptyList(),
    val exercises: List<CustomExercise> = emptyList(),
    val programs: List<Program> = emptyList(),
    val bands: List<Band> = emptyList(),
    val bellsKg: List<Double> = emptyList(),
    val barKg: Double? = null,
    val following: Followed? = null,
    val restSeconds: Int? = null,
)
