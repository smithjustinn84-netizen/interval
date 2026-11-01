package dev.justinsmith.lift.session

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.bandOf

// The Log sheet's draft as saved state holds it: the lift by key, the side by name (not every platform saves an
// enum) and the band by id, so a process killed with the sheet half set comes back to it.
internal fun SavedStateHandle.keep(draft: SetDraft) {
    this[EXERCISE] = draft.exercise.key
    this[BELL_KG] = draft.bellKg
    this[REPS] = draft.reps
    this[SIDE] = draft.side.name
    this[MINUTES] = draft.minutes
    this[DOUBLE] = draft.double
    this[ADDED_KG] = draft.addedKg
    this[BAR_KG] = draft.barKg
    this[SECONDS] = draft.seconds
    this[BAND] = draft.band.id
}

// Every field is kept together, so a draft without its lift was never kept; one of a lift no longer here is none.
internal fun SavedStateHandle.restoredDraft(made: List<CustomExercise>, bands: List<Band>): SetDraft? {
    val exercise = get<String>(EXERCISE)?.let { Exercise.parse(it, made) } ?: return null
    val blank = SetDraft()
    return SetDraft(
        exercise = exercise,
        bellKg = get<Double>(BELL_KG) ?: blank.bellKg,
        reps = get<Int>(REPS) ?: blank.reps,
        side = Side.entries.firstOrNull { it.name == get<String>(SIDE) } ?: blank.side,
        minutes = get<Int>(MINUTES) ?: blank.minutes,
        double = get<Boolean>(DOUBLE) ?: blank.double,
        addedKg = get<Double>(ADDED_KG) ?: blank.addedKg,
        barKg = get<Double>(BAR_KG) ?: blank.barKg,
        seconds = get<Int>(SECONDS) ?: blank.seconds,
        band = get<String>(BAND)?.let { bandOf(it, bands) } ?: blank.band,
    )
}

private const val EXERCISE = "set_exercise"
private const val BELL_KG = "set_bell_kg"
private const val REPS = "set_reps"
private const val SIDE = "set_side"
private const val MINUTES = "set_minutes"
private const val DOUBLE = "set_double"
private const val ADDED_KG = "set_added_kg"
private const val BAR_KG = "set_bar_kg"
private const val SECONDS = "set_seconds"
private const val BAND = "set_band"
