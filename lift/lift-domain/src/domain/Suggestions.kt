package dev.justinsmith.lift.domain

/** The band to suggest for [exercise]: the last one used for it if [offered] still, else [fallback]. */
fun suggestedBand(exercise: Exercise, history: List<SetLog>, offered: List<Band>, fallback: Band): Band {
    val last = history.lastOrNull { it.exercise.key == exercise.key && it.load.band != null }?.load?.band
    return offered.firstOrNull { it.id == last?.id } ?: fallback
}

/**
 * The bell to suggest for [exercise], with two bells or one ([double]): the last one used for it so, else
 * the last used for it at all, else [fallback].
 */
fun suggestedBell(exercise: Exercise, double: Boolean, history: List<SetLog>, fallback: Double): Double =
    history.lastOrNull { it.exercise.key == exercise.key && it.double == double }?.load?.kg
        ?: history.lastOrNull { it.exercise.key == exercise.key }?.load?.kg
        ?: fallback

/**
 * This load as [exercise] was last done with in [history], its kind kept: the bell last used (two or one, as
 * this is), the bar's last total or the user's [bar], the weight last added (none at first), the band last used
 * while still among [bands]; else as it is. The mat stays the mat.
 */
fun Load.suggestedFor(exercise: Exercise, history: List<SetLog>, bar: Double?, bands: List<Band>): Load =
    when (this) {
        is Load.Bell -> copy(kg = suggestedBell(exercise, double, history, fallback = kg))
        is Load.Bar -> Load.Bar(suggestedBar(exercise, history, bar))
        is Load.Bodyweight -> Load.Bodyweight(suggestedBell(exercise, double = false, history, fallback = 0.0))
        is Load.Banded -> Load.Banded(suggestedBand(exercise, history, bands, band ?: Band.Default))
        Load.Mat -> this
    }

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
 * The load for [set], of its lift's equipment: its weight as [suggestedLoad] has it (from [bellKg] at the
 * least), two bells as planned, and its band as [plannedBand] has it (from [band] at the least).
 */
@Suppress("detekt.LongParameterList") // What a suggestion goes by.
fun plannedLoad(
    set: PlannedSet,
    history: List<SetLog>,
    bar: Double?,
    offered: List<Band>,
    bellKg: Double,
    band: Band,
): Load = Load.of(
    set.exercise.equipment,
    suggestedLoad(set, history, bar, bellKg),
    set.double,
    plannedBand(set, history, offered, band),
)

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
