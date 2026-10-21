package dev.justinsmith.interval.timer

import kotlin.math.roundToInt

/** How hard the work intervals are. Each [Activity] gives its own MET for each level. */
enum class Effort { Easy, Steady, Hard, Max }

/**
 * What the work intervals are spent doing, with its MET (a multiple of the energy spent sitting still) at
 * each [Effort]. Rest and get ready count as [RECOVERY_MET] whatever the activity.
 *
 * Values are from the 2024 Adult Compendium of Physical Activities (pacompendium.com), by code; a value
 * marked "between" sits between two entries where the Compendium has none of its own.
 */
enum class Activity(
    private val easy: Double,
    private val steady: Double,
    private val hard: Double,
    private val max: Double,
) {
    /** High intensity intervals of no particular kind: 02035, 02210, between, 02214. */
    General(easy = 5.0, steady = 7.0, hard = 9.0, max = 11.0),

    /** Stationary or air bike, by watts: 01216, 01224, 01232, 01240. */
    Bike(easy = 5.0, steady = 6.8, hard = 10.3, max = 12.5),

    /** Rowing ergometer, by watts: 02071, 02072, 02073, 02074. */
    Rower(easy = 5.0, steady = 7.5, hard = 11.0, max = 14.0),

    /** Ski ergometer: 02080, 02082, between, 02084. */
    SkiErg(easy = 6.8, steady = 10.5, hard = 14.0, max = 18.0),

    /** Running, by speed (6, 7.5, 10 and 12 mph): 12050, 12080, 12120, 12132. */
    Running(easy = 9.3, steady = 11.8, hard = 14.8, max = 18.5),

    /** Rope skipping: 02069, 02068, between, and the fast pace (120-160 skips a minute) of the sports section. */
    JumpRope(easy = 9.0, steady = 11.0, hard = 11.8, max = 12.3),

    /** Burpees, squats, push-ups: 02022, 02057, 02020, 02214. */
    Bodyweight(easy = 3.8, steady = 6.5, hard = 7.5, max = 11.0),

    /** Kettlebell circuits and swings: 02035, 02040, between, 02058. */
    Kettlebell(easy = 5.0, steady = 7.5, hard = 8.5, max = 9.8),

    /** Barbell and Olympic lifting: 02054, 02052, 02050, and vigorous circuits with little rest, 02040. */
    Barbell(easy = 3.5, steady = 5.0, hard = 6.0, max = 7.5),

    /**
     * Climbing and hangboard: below an easy traverse (an estimate: hangs are short and mostly still), then
     * the Compendium's rock climbing entries for a low-to-moderate traverse, a hard ascent, and general.
     */
    Climbing(easy = 4.0, steady = 5.8, hard = 7.5, max = 8.0),
    ;

    fun met(effort: Effort): Double = when (effort) {
        Effort.Easy -> easy
        Effort.Steady -> steady
        Effort.Hard -> hard
        Effort.Max -> max
    }

    companion object {
        /** The activity stored under [name]; none, or one this build doesn't know, reads as [General]. */
        fun named(name: String?): Activity = entries.firstOrNull { it.name == name } ?: General
    }
}

/** Breathing hard between sets: well above sitting still, well below working. */
const val RECOVERY_MET = 2.5

/** How body weight is shown and edited. It is always stored in kilograms. */
enum class WeightUnit(val range: IntRange, val default: Int) {
    Kg(range = 30..250, default = 70),
    Lb(range = 66..550, default = 155),
}

const val POUNDS_PER_KG = 2.20462

/** [kg] in whole [unit]s, as the settings show it. */
fun WeightUnit.fromKg(kg: Double): Int = when (this) {
    WeightUnit.Kg -> kg
    WeightUnit.Lb -> kg * POUNDS_PER_KG
}.roundToInt()

/** A whole number of [unit]s in kilograms. */
fun WeightUnit.toKg(value: Int): Double = when (this) {
    WeightUnit.Kg -> value.toDouble()
    WeightUnit.Lb -> value / POUNDS_PER_KG
}

/**
 * The calories a workout burned, by the ACSM equation (kcal a minute = MET × 3.5 × kg / 200): [workSeconds]
 * of [activity] at [effort], and [otherSeconds] (get ready and rest) at [RECOVERY_MET]. An estimate, rounded.
 */
fun estimateCalories(
    workSeconds: Int,
    otherSeconds: Int,
    weightKg: Double,
    effort: Effort,
    activity: Activity = Activity.General,
): Int {
    val metSeconds = workSeconds * activity.met(effort) + otherSeconds.coerceAtLeast(0) * RECOVERY_MET
    return (metSeconds / SECONDS_PER_MINUTE.toDouble() * OXYGEN_ML_PER_MET * weightKg / KCAL_DIVISOR).roundToInt()
}

/**
 * The work time of a workout recorded before work time was measured: the whole plan for a finished one,
 * or, for one [endedEarly], what [elapsedSeconds] of the plan covers. Skips and added time make this a
 * guess for those.
 */
fun IntervalConfig.plannedWorkSeconds(elapsedSeconds: Int, endedEarly: Boolean): Int {
    val all = rounds * workSeconds
    if (!endedEarly) return all
    val intoRounds = (elapsedSeconds - prepSeconds).coerceAtLeast(0)
    val cycle = workSeconds + restSeconds
    val work = intoRounds / cycle * workSeconds + minOf(intoRounds % cycle, workSeconds)
    return work.coerceAtMost(all)
}

private const val OXYGEN_ML_PER_MET = 3.5
private const val KCAL_DIVISOR = 200.0
