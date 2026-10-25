package dev.justinsmith.bells.domain

import kotlin.math.abs
import kotlin.math.round

/** The bars Bells offers, in kilograms: a technique bar, a women's bar and a men's. */
val bars: List<Double> = listOf(10.0, 15.0, 20.0)

/** The bar a set of a barbell lift starts on when the user hasn't said which they have. */
const val DEFAULT_BAR_KG = 20.0

/** The plates a bar is loaded with, heaviest first: the competition colours and the change plates. */
val standardPlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/** What the total on a bar goes up or down by: the lightest pair of plates. */
const val BAR_KG_STEP = 2.5

/** The heaviest total a barbell set takes. */
const val MAX_BAR_KG = 500.0

/**
 * The plates that make [totalKg] on a [barKg] bar, heaviest first, for one side: none for the empty bar, null
 * when the total is under the bar or can't be made from [standardPlates].
 */
fun platesPerSide(totalKg: Double, barKg: Double): List<Double>? {
    // In the lightest plate's units, so the sums are exact.
    var left = units(totalKg - barKg)
    if (left == null || left < 0 || left % 2 != 0L) return null
    left /= 2
    val plates = mutableListOf<Double>()
    for (plate in standardPlates) {
        val size = units(plate) ?: continue
        while (left >= size) {
            plates += plate
            left -= size
        }
    }
    return plates.takeIf { left == 0L }
}

/** [kg] in the lightest plate's units, or null when it isn't a whole number of them. */
private fun units(kg: Double): Long? {
    val scaled = kg / standardPlates.last()
    val whole = round(scaled).toLong()
    return whole.takeIf { abs(scaled - whole) < EPSILON }
}

private const val EPSILON = 1e-6

/**
 * The total to suggest for a barbell [exercise]: the last used for it, else the user's [bar] empty, else
 * [DEFAULT_BAR_KG].
 */
fun suggestedBar(exercise: Exercise, history: List<SetLog>, bar: Double?): Double =
    history.lastOrNull { it.exercise.key == exercise.key }?.bellKg ?: bar ?: DEFAULT_BAR_KG
