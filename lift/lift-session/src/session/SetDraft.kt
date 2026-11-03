package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.DEFAULT_BELL_KG
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.MAX_BAR_KG
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.other
import dev.justinsmith.lift.domain.suggestedFor

/**
 * The set the Log sheet will log, kept between sets so the next starts where the last left off. Each kind of
 * load keeps its own value, so going from a bell to a band and back finds the bell where it was.
 */
data class SetDraft(
    val exercise: Exercise = BuiltInExercise.TwoHandSwing,
    val bellKg: Double = DEFAULT_BELL_KG,
    val reps: Int = DEFAULT_REPS,
    val side: Side = Side.Left,
    /** For an EMOM started from the sheet. */
    val minutes: Int = DEFAULT_MINUTES,
    /** Two bells asked for: kept while a lift that can't be doubled is picked, for the next that can. */
    val double: Boolean = false,
    /** Weight added to a bodyweight exercise; 0 for none. */
    val addedKg: Double = 0.0,
    /** The total on the bar, for a barbell lift. */
    val barKg: Double = DEFAULT_BAR_KG,
    /** How long a hold lasted. */
    val seconds: Int = DEFAULT_SECONDS,
    val band: Band = Band.Default,
) {
    /** Whether the set is done with two bells. */
    val doubled: Boolean get() = exercise.doubled(double)

    /**
     * The load the set is logged with, as the lift's equipment takes it: the bell (or two), the bar, the weight
     * added, the band, or the mat.
     */
    val load: Load get() = when (exercise.equipment) {
        Equipment.Kettlebell -> Load.Bell(bellKg, doubled)
        Equipment.Barbell -> Load.Bar(barKg)
        Equipment.Bodyweight -> Load.Bodyweight(addedKg)
        Equipment.Band -> Load.Banded(band)
        Equipment.Mat -> Load.Mat
    }

    /** This draft with [load] kept as its kind's value; the other kinds' stay. Two bells or not stays too. */
    fun keeping(load: Load): SetDraft = when (load) {
        is Load.Bell -> copy(bellKg = load.kg)
        is Load.Bar -> copy(barKg = load.kg)
        is Load.Bodyweight -> copy(addedKg = load.kg)
        is Load.Banded -> load.band?.let { copy(band = it) } ?: this
        Load.Mat -> this
    }

    /** Whether the set is a hold, timed rather than counted. */
    val held: Boolean get() = exercise.measure == Measure.Time

    companion object {
        const val DEFAULT_REPS = 10
        const val DEFAULT_MINUTES = 10
        const val DEFAULT_SECONDS = 30
        const val MAX_REPS = 999
        const val MAX_MINUTES = 60

        /** A hold steps by five seconds, up to ten minutes. */
        const val SECONDS_STEP = 5
        const val MAX_SECONDS = 600
    }
}

/** This draft's load as its lift was last done in [history], or as it is when the lift has none. */
internal fun SetDraft.suggested(history: List<SetLog>, bar: Double?, bands: List<Band>): SetDraft =
    keeping(load.suggestedFor(exercise, history, bar, bands))

/** This draft with each count in its range, and the bar loaded with no less than the user's [bar] weighs. */
internal fun SetDraft.clamped(bar: Double?): SetDraft = copy(
    barKg = barKg.coerceIn(bar ?: 0.0, MAX_BAR_KG),
    reps = reps.coerceIn(1, SetDraft.MAX_REPS),
    minutes = minutes.coerceIn(1, SetDraft.MAX_MINUTES),
    seconds = seconds.coerceIn(SetDraft.SECONDS_STEP, SetDraft.MAX_SECONDS),
)

/**
 * This draft as [set] has it, for its equipment; the rest stays. With [flipSide], the other side of a one-sided
 * lift, to go on with.
 */
internal fun SetDraft.from(set: SetLog, flipSide: Boolean): SetDraft {
    val side = set.side ?: side
    return keeping(set.load).copy(
        exercise = set.exercise,
        reps = set.reps.coerceAtLeast(1),
        side = if (flipSide && set.exercise.sided(set.double)) side.other else side,
        double = set.double,
        seconds = set.seconds ?: seconds,
    )
}

/** This draft as [next] plans it: its lift, load, reps or seconds, side and bells; the rest stays. */
internal fun SetDraft.planned(next: UpNext): SetDraft {
    val set = next.set
    return keeping(next.load).copy(
        exercise = set.exercise,
        reps = if (set.held) reps else set.reps.coerceIn(1, SetDraft.MAX_REPS),
        seconds = if (set.held) set.reps.coerceIn(SetDraft.SECONDS_STEP, SetDraft.MAX_SECONDS) else seconds,
        side = set.side ?: side,
        double = set.double,
    )
}

/** The logged [set] changed to what this draft has: its load, reps or seconds, side, bells and band. */
internal fun SetDraft.appliedTo(set: SetLog): SetLog = set.copy(
    load = load,
    reps = if (held) 0 else reps,
    side = side,
    seconds = seconds.takeIf { held },
)
