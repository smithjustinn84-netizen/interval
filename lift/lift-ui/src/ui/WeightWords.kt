package dev.justinsmith.lift.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.LocalWeightUnit
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.bellWeight
import dev.justinsmith.lift.domain.displayWeight
import dev.justinsmith.lift.domain.formatWeightNumber
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.load_double
import dev.justinsmith.lift.ui.resources.unit_kg
import dev.justinsmith.lift.ui.resources.unit_lb
import dev.justinsmith.lift.ui.resources.weight_value
import org.jetbrains.compose.resources.stringResource

/** The active weight unit's label: "kg" or "lb". */
@Composable
fun unitLabel(unit: WeightUnit = LocalWeightUnit.current): String = stringResource(
    when (unit) {
        WeightUnit.Kg -> Res.string.unit_kg
        WeightUnit.Lb -> Res.string.unit_lb
    },
)

/** A weight [kg] of [equipment] formatted in [unit]: "24 kg", "53 lb", "45 lb", "135 lb". */
@Composable
fun weightValue(kg: Double, equipment: Equipment, unit: WeightUnit = LocalWeightUnit.current): String =
    stringResource(
        Res.string.weight_value,
        formatWeightNumber(displayWeight(kg, equipment, unit)),
        unitLabel(unit),
    )

/** A pair of bells of [kg] each, formatted in [unit]: "2 × 16 kg" or "2 × 35 lb". */
@Composable
fun pairWeightValue(kg: Double, unit: WeightUnit = LocalWeightUnit.current): String =
    stringResource(Res.string.load_double, weightValue(kg, Equipment.Kettlebell, unit))

/** Total tonnage already computed in [unit], formatted with its label: "412.5 kg" or "900 lb". */
@Composable
fun tonnageValue(tonnage: Double, unit: WeightUnit = LocalWeightUnit.current): String =
    stringResource(Res.string.weight_value, formatWeightNumber(tonnage), unitLabel(unit))

/** A bell's bare weight number in [unit], for compact chips and the rack: "16" or "35". */
@Composable
fun bellNumber(kg: Double, unit: WeightUnit = LocalWeightUnit.current): String =
    formatWeightNumber(bellWeight(kg, unit))
