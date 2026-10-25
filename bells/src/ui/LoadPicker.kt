package dev.justinsmith.bells.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.BAR_KG_STEP
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.DEFAULT_BAR_KG
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.MAX_BAR_KG
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.steppedBand
import dev.justinsmith.bells.domain.steppedKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.load_last_time
import dev.justinsmith.bells.resources.load_set_weight
import dev.justinsmith.bells.resources.log_added
import dev.justinsmith.bells.resources.log_added_none
import dev.justinsmith.bells.resources.log_band
import dev.justinsmith.bells.resources.log_bar_weight
import dev.justinsmith.bells.resources.log_bell
import dev.justinsmith.bells.resources.log_heavier
import dev.justinsmith.bells.resources.log_lighter
import dev.justinsmith.bells.resources.log_no_bells
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.stringResource

/**
 * The load [exercise] takes: a bell (or two, when [double]), the total on the user's [bar], weight added to
 * bodyweight, a band, or nothing on the mat. [kg] is the bell, the total or the weight added, [band] the band,
 * each among [bells] and [bands]. With [lastTime], a plan's load, it can also be left to what was used last
 * time: null. Without [ownsBells] a hint says where to add them.
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
    modifier: Modifier = Modifier,
    lastTime: Boolean = false,
    ownsBells: Boolean = true,
    bar: Double? = null,
) {
    val nothing: List<Nothing?> = if (lastTime) listOf(null) else emptyList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        when (exercise.equipment) {
            Equipment.Kettlebell -> BellChoices(exercise, double, kg, nothing + bells, onKg, onDouble, ownsBells)

            Equipment.Barbell -> BarChoices(kg, bar, lastTime, onKg)

            Equipment.Bodyweight -> AddedChoices(kg, nothing + listOf(0.0) + bells, onKg)

            Equipment.Band -> {
                GroupLabel(stringResource(Res.string.log_band))
                // The band picked, as it now stands: renamed since, it shows by its new name.
                val picked = band?.let { b -> bands.firstOrNull { it.id == b.id } ?: b }
                val last = stringResource(Res.string.load_last_time)
                if (!lastTime && picked != null) {
                    LoadStepper(
                        bandName(picked),
                        canLower = steppedBand(picked, -1, bands) != null,
                        canRaise = steppedBand(picked, 1, bands) != null,
                        onStep = { step -> steppedBand(picked, step, bands)?.let(onBand) },
                    )
                }
                ChoiceRow(nothing + bands, picked, { it?.let { b -> bandName(b) } ?: last }, onBand)
            }

            Equipment.Mat -> Unit
        }
    }
}

/** A bell to pick among [options] (null for last time), two of it when [double], and where to add bells. */
@Composable
private fun BellChoices(
    exercise: Exercise,
    double: Boolean,
    kg: Double?,
    options: List<Double?>,
    onKg: (Double?) -> Unit,
    onDouble: () -> Unit,
    ownsBells: Boolean,
) {
    val last = stringResource(Res.string.load_last_time)
    GroupLabel(stringResource(Res.string.log_bell))
    if (kg != null && null !in options) {
        KgStepper(
            loadLine(kg, exercise.doubled(double), Equipment.Kettlebell, null),
            kg,
            Equipment.Kettlebell,
            options,
            onKg,
        )
    }
    ChoiceRow(options, kg, { it?.let(::formatKg) ?: last }, onKg)
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

/** Weight added to bodyweight, among [options]: none (0), a bell's weight, or null for last time. */
@Composable
private fun AddedChoices(kg: Double?, options: List<Double?>, onKg: (Double?) -> Unit) {
    val last = stringResource(Res.string.load_last_time)
    val none = stringResource(Res.string.log_added_none)
    GroupLabel(stringResource(Res.string.log_added))
    if (kg != null && null !in options) {
        KgStepper(loadLine(kg, false, Equipment.Bodyweight, null), kg, Equipment.Bodyweight, options, onKg)
    }
    ChoiceRow(options, kg, { option -> option?.let { if (it == 0.0) none else formatKg(it) } ?: last }, onKg)
}

/**
 * The total on the bar, in steps of [BAR_KG_STEP] from the empty [bar] up, and the plates a side it takes. For
 * a plan ([lastTime]) it can be left to last time (a null [kg]) instead.
 */
@Composable
private fun BarChoices(kg: Double?, bar: Double?, lastTime: Boolean, onKg: (Double?) -> Unit) {
    GroupLabel(stringResource(Res.string.log_bar_weight))
    if (lastTime) {
        val last = stringResource(Res.string.load_last_time)
        val set = stringResource(Res.string.load_set_weight)
        ChoiceRow(listOf(false, true), kg != null, { if (it) set else last }, { named ->
            onKg(if (named) kg ?: bar ?: DEFAULT_BAR_KG else null)
        })
    }
    if (kg == null) return
    val lightest = bar ?: 0.0
    LoadStepper(
        stringResource(Res.string.kg_value, formatKg(kg)),
        canLower = kg - BAR_KG_STEP >= lightest,
        canRaise = kg + BAR_KG_STEP <= MAX_BAR_KG,
        onStep = { step -> onKg((kg + step * BAR_KG_STEP).coerceIn(lightest, MAX_BAR_KG)) },
        note = platesLine(kg, bar),
    )
}

/**
 * A load, large ([compact]: a tile's number), with Lighter and Heavier that repeat while held, each [onStep] a
 * step down or up; under it a [note], such as the plates a side.
 */
@Composable
internal fun LoadStepper(
    value: String,
    canLower: Boolean,
    canRaise: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    compact: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Stepper(
            value,
            fewer = stringResource(Res.string.log_lighter),
            more = stringResource(Res.string.log_heavier),
            canLower = canLower,
            canRaise = canRaise,
            onStep = onStep,
            compact = compact,
        )
        note?.let { Text(it, style = IntervalTheme.type.meta, color = IntervalTheme.palette.textDim) }
    }
}

/** [value], the [kg] a lift done with [equipment] takes, a step at a time among [options]. */
@Composable
private fun KgStepper(
    value: String?,
    kg: Double,
    equipment: Equipment,
    options: List<Double?>,
    onKg: (Double?) -> Unit,
) {
    val bells = options.filterNotNull()
    LoadStepper(
        value.orEmpty(),
        canLower = steppedKg(equipment, kg, -1, bells, bar = null) != null,
        canRaise = steppedKg(equipment, kg, 1, bells, bar = null) != null,
        onStep = { step -> steppedKg(equipment, kg, step, bells, bar = null)?.let(onKg) },
    )
}
