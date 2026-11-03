package dev.justinsmith.lift.fakes

import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side

/** Logs a set of [exercise] at [bellKg] (the bell, the bar, the weight added), two bells or not, or with [band]. */
fun SetStore.logSet(
    exercise: Exercise,
    bellKg: Double,
    reps: Int,
    side: Side?,
    double: Boolean = false,
    seconds: Int? = null,
    band: Band? = null,
) = logSet(exercise, Load.of(exercise.equipment, bellKg, double, band), reps, side, seconds)
