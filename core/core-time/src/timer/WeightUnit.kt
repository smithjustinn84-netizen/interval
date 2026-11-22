package dev.justinsmith.interval.timer

import kotlin.math.roundToInt

/** How weights (body weight, bells, bars and tonnage) are shown and edited. Stored always in kilograms. */
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
