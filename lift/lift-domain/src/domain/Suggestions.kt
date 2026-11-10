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

/** The bell a lift held overhead starts at when the user has said nothing of the bells they own. */
const val LIGHT_BELL_KG = 8.0

/**
 * Whether a lift starts light: a kettlebell pressed or held overhead, or upside down, or taken through a get-up or
 * a windmill, where a first try at the bell that swings well is how a shoulder gets hurt.
 */
val Exercise.startsLight: Boolean
    get() = equipment == Equipment.Kettlebell &&
        (movement.pattern in lightPatterns || key == BuiltInExercise.BottomsUpCarry.key)

private val lightPatterns = setOf(Pattern.Press, Pattern.GetUp, Pattern.Core)

/**
 * The bell [exercise] starts at with no history: [fallback], or for a lift that [startsLight] the lightest of the
 * bells [owned] ([LIGHT_BELL_KG] with none said), never above [fallback].
 */
fun startingBellKg(exercise: Exercise, owned: List<Double>, fallback: Double = DEFAULT_BELL_KG): Double =
    if (exercise.startsLight) minOf(fallback, owned.minOrNull() ?: LIGHT_BELL_KG) else fallback

/** What [exercise] starts at with nothing to go on: the standard bar for a barbell lift, else [DEFAULT_BELL_KG]. */
fun startingLoadKg(exercise: Exercise): Double =
    if (exercise.equipment == Equipment.Barbell) DEFAULT_BAR_KG else DEFAULT_BELL_KG

/**
 * The load to suggest for [set]: the plan's, when it names one, else of its lift's equipment as it was last done
 * in [history] ([suggestedFor]). With nothing to go on a bell is [bellKg], or for a lift that starts light the
 * lightest of those [owned] ([startingBellKg]), a bar the user's [bar], weight added to bodyweight none and a band
 * [band]. A band the plan names stands only while still [offered]; else the one last
 * used for it.
 */
@Suppress("detekt.LongParameterList") // What a suggestion goes by.
fun plannedLoad(
    set: PlannedSet,
    history: List<SetLog>,
    bar: Double?,
    offered: List<Band>,
    bellKg: Double,
    band: Band,
    owned: List<Double>,
): Load {
    val unnamed = Load.of(set.exercise.equipment, startingBellKg(set.exercise, owned, bellKg), set.double, band)
    return when (val named = set.load) {
        null -> unnamed.suggestedFor(set.exercise, history, bar, offered)

        is Load.Banded -> named.band?.let { planned -> offered.firstOrNull { it.id == planned.id } }
            ?.let(Load::Banded)
            ?: unnamed.suggestedFor(set.exercise, history, bar, offered)

        is Load.Bell, is Load.Bar, is Load.Bodyweight, Load.Mat -> named
    }
}
