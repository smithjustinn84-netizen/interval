package dev.justinsmith.bells.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.load_last_time
import dev.justinsmith.bells.resources.log_added
import dev.justinsmith.bells.resources.log_added_none
import dev.justinsmith.bells.resources.log_band
import dev.justinsmith.bells.resources.log_bell
import dev.justinsmith.bells.resources.log_no_bells
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.stringResource

/**
 * The load [exercise] takes: a bell (or two, when [double]), weight added to bodyweight, a band, or nothing on
 * the mat. [kg] is the bell or the weight added, [band] the band, each among [bells] and [bands]. With
 * [lastTime], a plan's load, it can also be left to what was used last time: null. Without [ownsBells] a hint
 * says where to add them.
 */
@Composable
fun LoadPicker(
    exercise: Exercise,
    double: Boolean,
    kg: Double?,
    band: Band?,
    bells: List<Double>,
    bands: List<Band>,
    onKg: (Double?) -> Unit,
    onBand: (Band?) -> Unit,
    onDouble: () -> Unit,
    lastTime: Boolean = false,
    ownsBells: Boolean = true,
) {
    val last = stringResource(Res.string.load_last_time)
    val nothing: List<Nothing?> = if (lastTime) listOf(null) else emptyList()
    when (exercise.equipment) {
        Equipment.Kettlebell -> {
            GroupLabel(stringResource(Res.string.log_bell))
            ChoiceRow(nothing + bells, kg, { it?.let(::formatKg) ?: last }, onKg)
            if (!ownsBells) {
                Text(
                    stringResource(Res.string.log_no_bells),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.palette.textDim,
                )
            }
            if (exercise.doubles == Doubles.Optional) {
                ListCard {
                    ToggleRow(stringResource(Res.string.lift_double), null, checked = double, onToggle = onDouble)
                }
            }
        }

        Equipment.Bodyweight -> {
            GroupLabel(stringResource(Res.string.log_added))
            val none = stringResource(Res.string.log_added_none)
            ChoiceRow(
                nothing + listOf(0.0) + bells,
                kg,
                { kg -> kg?.let { if (it == 0.0) none else formatKg(it) } ?: last },
                onKg,
            )
        }

        Equipment.Band -> {
            GroupLabel(stringResource(Res.string.log_band))
            // The band picked, as it now stands: renamed since, it shows by its new name.
            val picked = band?.let { b -> bands.firstOrNull { it.id == b.id } ?: b }
            ChoiceRow(nothing + bands, picked, { it?.let { b -> bandName(b) } ?: last }, onBand)
        }

        Equipment.Mat -> Unit
    }
}
