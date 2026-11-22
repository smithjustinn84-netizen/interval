package dev.justinsmith.lift.domain

import dev.justinsmith.interval.timer.WeightUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** The bars Lift offers, in kilograms: a technique bar, a women's bar and a men's. */
val bars: List<Double> = listOf(10.0, 15.0, 20.0)

/** The bar a set of a barbell lift starts on when the user hasn't said which they have. */
const val DEFAULT_BAR_KG = 20.0

/** The plates a bar is loaded with in kilograms, heaviest first: the competition colours and the change plates. */
val standardPlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/** The plates a bar is loaded with in pounds, heaviest first: standard US plates down to 2.5 lb change plates. */
val standardPlatesLb: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

/** What the total on a bar goes up or down by in kilograms: the lightest pair of plates. */
const val BAR_KG_STEP = 2.5

/** What the total on a bar goes up or down by in pounds: a pair of 2.5 lb plates. */
const val BAR_LB_STEP = 5.0

/** The heaviest total a barbell set takes in kilograms. */
const val MAX_BAR_KG = 500.0

/** The heaviest total a barbell set takes in pounds (under [MAX_BAR_KG], a multiple of [BAR_LB_STEP]). */
const val MAX_BAR_LB = 1100.0

/**
 * The plates that make [totalKg] on a [barKg] bar in [unit], heaviest first, for one side: none for the empty bar,
 * null when the total is under the bar or can't be made from the unit's standard plates.
 */
fun platesPerSide(totalKg: Double, barKg: Double, unit: WeightUnit = WeightUnit.Kg): List<Double>? {
    val (total, bar, plateSet) = when (unit) {
        WeightUnit.Kg -> Triple(totalKg, barKg, standardPlates)
        WeightUnit.Lb -> Triple(barWeight(totalKg, WeightUnit.Lb), barWeight(barKg, WeightUnit.Lb), standardPlatesLb)
    }
    val smallest = plateSet.last()
    // In the lightest plate's units, so the sums are exact.
    var left = units(total - bar, smallest)
    if (left == null || left < 0 || left % 2 != 0L) return null
    left /= 2
    val plates = mutableListOf<Double>()
    for (plate in plateSet) {
        val size = units(plate, smallest) ?: continue
        while (left >= size) {
            plates += plate
            left -= size
        }
    }
    return plates.takeIf { left == 0L }
}

/**
 * Steps a barbell total [kg] by [step] (−1 lighter, +1 heavier) in [unit], never below the empty [bar] or above
 * the bar's maximum. In [WeightUnit.Lb] steps snap to [BAR_LB_STEP] multiples and land back on the canonical [bar]
 * kg when stepping down to the empty bar. Null when the step leaves the allowed range.
 */
fun stepBarKg(kg: Double, step: Int, bar: Double?, unit: WeightUnit = WeightUnit.Kg): Double? = when (unit) {
    WeightUnit.Kg -> (kg + step * BAR_KG_STEP).takeIf { it >= (bar ?: 0.0) && it <= MAX_BAR_KG }

    WeightUnit.Lb -> {
        val currentLb = barWeight(kg, WeightUnit.Lb)
        val barLb = bar?.let { barWeight(it, WeightUnit.Lb) } ?: 0.0
        val nextLb = when {
            step > 0 -> (floor(currentLb / BAR_LB_STEP) + step) * BAR_LB_STEP
            step < 0 -> (ceil(currentLb / BAR_LB_STEP) + step) * BAR_LB_STEP
            else -> currentLb
        }
        when {
            nextLb < barLb || nextLb > MAX_BAR_LB -> null
            bar != null && abs(nextLb - barLb) < EPSILON -> bar
            else -> lbToKg(nextLb)
        }
    }
}

/** [value] in [smallest] plate units, or null when it isn't a whole number of them. */
private fun units(value: Double, smallest: Double): Long? {
    val scaled = value / smallest
    val whole = round(scaled).toLong()
    return whole.takeIf { abs(scaled - whole) < EPSILON }
}

private const val EPSILON = 1e-6

/**
 * The total to suggest for a barbell [exercise]: the last used for it, else the user's [bar] empty, else
 * [DEFAULT_BAR_KG].
 */
fun suggestedBar(exercise: Exercise, history: List<SetLog>, bar: Double?): Double =
    history.lastOrNull { it.exercise.key == exercise.key && !it.warmUp }?.load?.kg ?: bar ?: DEFAULT_BAR_KG
