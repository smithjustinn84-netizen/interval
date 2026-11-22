package dev.justinsmith.lift.domain

import dev.justinsmith.interval.timer.POUNDS_PER_KG
import dev.justinsmith.interval.timer.WeightUnit
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Canonical US kettlebell weights in pounds for each size in [standardBells], matching the pood / cast-iron
 * convention (e.g. 16 kg -> 35 lb, 24 kg -> 53 lb, 32 kg -> 70 lb).
 */
private val standardBellLb: Map<Double, Double> = mapOf(
    4.0 to 9.0,
    6.0 to 13.0,
    8.0 to 18.0,
    10.0 to 22.0,
    12.0 to 26.0,
    14.0 to 31.0,
    16.0 to 35.0,
    18.0 to 40.0,
    20.0 to 44.0,
    22.0 to 48.0,
    24.0 to 53.0,
    26.0 to 57.0,
    28.0 to 62.0,
    30.0 to 66.0,
    32.0 to 70.0,
    36.0 to 79.0,
    40.0 to 88.0,
    44.0 to 97.0,
    48.0 to 106.0,
)

/** Canonical US Olympic bar weights in pounds for each bar in [bars] (10/15/20 kg -> 25/35/45 lb). */
private val standardBarLb: Map<Double, Double> = mapOf(
    10.0 to 25.0,
    15.0 to 35.0,
    20.0 to 45.0,
)

private const val HALF_SCALE = 2.0

/** Converts a weight in pounds to kilograms for canonical storage. */
fun lbToKg(lb: Double): Double = lb / POUNDS_PER_KG

/**
 * A kettlebell (or added bodyweight) weight [kg] shown in [unit]: canonical US bell sizes in [WeightUnit.Lb]
 * (18, 26, 35, 44, 53, 70 lb for 8, 12, 16, 20, 24, 32 kg), falling back to whole pounds for non-standard sizes.
 */
fun bellWeight(kg: Double, unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> kg
    WeightUnit.Lb -> standardBellLb[kg] ?: (kg * POUNDS_PER_KG).roundToInt().toDouble()
}

/**
 * A barbell weight [kg] shown in [unit]: canonical US bar weights (25, 35, 45 lb for 10, 15, 20 kg) in
 * [WeightUnit.Lb], otherwise rounded to the nearest half pound so every 5 lb and 2.5 lb step round-trips cleanly.
 */
fun barWeight(kg: Double, unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> kg
    WeightUnit.Lb -> standardBarLb[kg] ?: (round(kg * POUNDS_PER_KG * HALF_SCALE) / HALF_SCALE)
}

/** A weight [kg] of [equipment] shown in [unit]. */
fun displayWeight(kg: Double, equipment: Equipment, unit: WeightUnit): Double = when (equipment) {
    Equipment.Kettlebell, Equipment.Bodyweight -> bellWeight(kg, unit)
    Equipment.Barbell -> barWeight(kg, unit)
    Equipment.Band, Equipment.Mat -> 0.0
}

/** The weight one rep of this load moves, in [unit]: both bells of a pair, or 0 for bands and the mat. */
fun Load.movedWeight(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> movedKg

    WeightUnit.Lb -> when (this) {
        is Load.Bell -> bellWeight(kg, WeightUnit.Lb) * (if (double) 2 else 1)
        is Load.Bar -> barWeight(kg, WeightUnit.Lb)
        is Load.Bodyweight -> bellWeight(kg, WeightUnit.Lb)
        is Load.Banded, Load.Mat -> 0.0
    }
}

/** The weight this set moved in [unit]: [Load.movedWeight] times its reps. */
fun SetLog.tonnage(unit: WeightUnit): Double = load.movedWeight(unit) * reps

/**
 * The total weight this session's working sets moved in [unit], summing each set's displayed weight times its reps:
 * warm-ups left out in either unit, as [LiftSession.tonnageKg] leaves them.
 */
fun LiftSession.tonnage(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> tonnageKg
    WeightUnit.Lb -> workingSets.sumOf { it.tonnage(WeightUnit.Lb) }
}

/** Formats a weight number in either unit, trimming trailing zeros as [formatKg] does. */
fun formatWeightNumber(value: Double): String = formatKg(value)
