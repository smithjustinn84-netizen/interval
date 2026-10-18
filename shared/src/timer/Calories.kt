package dev.justinsmith.interval.timer

import kotlin.math.roundToInt

/**
 * How hard the work intervals are, as a MET: a multiple of the energy spent sitting still. Rest and get
 * ready count as [RECOVERY_MET] whatever the effort.
 */
enum class Effort(val met: Double) {
    Easy(met = 4.0),
    Steady(met = 6.0),
    Hard(met = 8.0),
    Max(met = 10.0),
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
 * at [effort], and [otherSeconds] (get ready and rest) at [RECOVERY_MET]. An estimate, rounded.
 */
fun estimateCalories(workSeconds: Int, otherSeconds: Int, weightKg: Double, effort: Effort): Int {
    val metSeconds = workSeconds * effort.met + otherSeconds.coerceAtLeast(0) * RECOVERY_MET
    return (metSeconds / SECONDS_PER_MINUTE * OXYGEN_ML_PER_MET * weightKg / KCAL_DIVISOR).roundToInt()
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

private const val SECONDS_PER_MINUTE = 60.0
private const val OXYGEN_ML_PER_MET = 3.5
private const val KCAL_DIVISOR = 200.0
