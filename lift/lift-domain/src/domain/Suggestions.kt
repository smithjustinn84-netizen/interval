package dev.justinsmith.lift.domain

/** The band to suggest for [exercise]: the last one used for it if [offered] still, else [fallback]. */
fun suggestedBand(exercise: Exercise, history: List<SetLog>, offered: List<Band>, fallback: Band): Band {
    val last = history.lastOrNull { it.exercise.key == exercise.key && it.band != null }?.band
    return offered.firstOrNull { it.id == last?.id } ?: fallback
}

/**
 * The bell to suggest for [exercise], with two bells or one ([double]): the last one used for it so, else
 * the last used for it at all, else [fallback].
 */
fun suggestedBell(exercise: Exercise, double: Boolean, history: List<SetLog>, fallback: Double): Double =
    history.lastOrNull { it.exercise.key == exercise.key && it.double == double }?.bellKg
        ?: history.lastOrNull { it.exercise.key == exercise.key }?.bellKg
        ?: fallback

/** The bell a lift starts at before it has a history or the user's own bells say otherwise. */
const val DEFAULT_BELL_KG = 16.0

/** What [exercise] starts at with nothing to go on: the standard bar for a barbell lift, else [DEFAULT_BELL_KG]. */
fun startingLoadKg(exercise: Exercise): Double =
    if (exercise.equipment == Equipment.Barbell) DEFAULT_BAR_KG else DEFAULT_BELL_KG

/**
 * The load to suggest for [set]: the plan's, when it names one, else what was used last in [history]. A bell
 * falls back to [bellKg], a bar to the user's [bar]; weight added to bodyweight starts at none, and a band
 * and the mat take no weight.
 */
fun suggestedLoad(set: PlannedSet, history: List<SetLog>, bar: Double?, bellKg: Double): Double =
    when (set.exercise.equipment) {
        Equipment.Kettlebell -> set.kg ?: suggestedBell(set.exercise, set.double, history, bellKg)
        Equipment.Barbell -> set.kg ?: suggestedBar(set.exercise, history, bar)
        Equipment.Bodyweight -> set.kg ?: suggestedBell(set.exercise, double = false, history, fallback = 0.0)
        Equipment.Band, Equipment.Mat -> 0.0
    }

/**
 * The band for [set], when it is band work: the one the plan names while still [offered], else the one
 * last used for it, else [fallback]. Null for any other set.
 */
fun plannedBand(set: PlannedSet, history: List<SetLog>, offered: List<Band>, fallback: Band): Band? =
    if (set.exercise.equipment == Equipment.Band) {
        offered.firstOrNull { it.id == set.band } ?: suggestedBand(set.exercise, history, offered, fallback)
    } else {
        null
    }
