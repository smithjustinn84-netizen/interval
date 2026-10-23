package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BandColour
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.platesPerSide
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.band_extra_heavy
import dev.justinsmith.bells.resources.band_extra_light
import dev.justinsmith.bells.resources.band_heavy
import dev.justinsmith.bells.resources.band_light
import dev.justinsmith.bells.resources.band_medium
import dev.justinsmith.bells.resources.band_unnamed
import dev.justinsmith.bells.resources.bar_empty
import dev.justinsmith.bells.resources.bar_plates
import dev.justinsmith.bells.resources.bar_too_light
import dev.justinsmith.bells.resources.bar_unloadable
import dev.justinsmith.bells.resources.colour_black
import dev.justinsmith.bells.resources.colour_blue
import dev.justinsmith.bells.resources.colour_green
import dev.justinsmith.bells.resources.colour_grey
import dev.justinsmith.bells.resources.colour_orange
import dev.justinsmith.bells.resources.colour_purple
import dev.justinsmith.bells.resources.colour_red
import dev.justinsmith.bells.resources.colour_yellow
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.equipment_band
import dev.justinsmith.bells.resources.equipment_barbell
import dev.justinsmith.bells.resources.equipment_bodyweight
import dev.justinsmith.bells.resources.equipment_kettlebell
import dev.justinsmith.bells.resources.equipment_mat
import dev.justinsmith.bells.resources.exercise_a_side
import dev.justinsmith.bells.resources.focus_core
import dev.justinsmith.bells.resources.focus_full_body
import dev.justinsmith.bells.resources.focus_lower_body
import dev.justinsmith.bells.resources.focus_upper_body
import dev.justinsmith.bells.resources.log_no_bar
import dev.justinsmith.bells.resources.measure_reps
import dev.justinsmith.bells.resources.measure_time
import dev.justinsmith.bells.resources.plates_plus
import dev.justinsmith.bells.resources.set_line
import dev.justinsmith.bells.resources.set_line_added
import dev.justinsmith.bells.resources.set_line_added_held
import dev.justinsmith.bells.resources.set_line_band
import dev.justinsmith.bells.resources.set_line_double
import dev.justinsmith.bells.resources.set_line_reps
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.stringResource

/** A logged set in words, as [setLine] puts it. */
@Composable
fun setLine(set: SetLog): String =
    setLine(set.bellKg, set.reps, set.side, set.double, set.equipment, set.seconds, set.band)

/**
 * A set in words, by what it was done with: a bell "24 kg × 10", two "2 × 16 kg × 5"; a bar "62.5 kg × 3";
 * bodyweight "× 15", or
 * "+10 kg × 6" with weight added; a band "Medium × 20"; the mat "× 10"; a hold "0:45", or "+10 kg · 0:45".
 * A one-sided set adds its side: "24 kg × 10 · Left".
 */
@Composable
fun setLine(
    bellKg: Double,
    reps: Int,
    side: Side?,
    double: Boolean = false,
    equipment: Equipment = Equipment.Kettlebell,
    seconds: Int? = null,
    band: Band? = null,
): String {
    val added = equipment == Equipment.Bodyweight && bellKg > 0
    val line = when {
        seconds != null && added -> stringResource(
            Res.string.set_line_added_held,
            formatKg(bellKg),
            formatClock(seconds),
        )

        seconds != null -> formatClock(seconds)

        equipment == Equipment.Kettlebell && double -> stringResource(
            Res.string.set_line_double,
            formatKg(bellKg),
            reps,
        )

        equipment == Equipment.Kettlebell || equipment == Equipment.Barbell ->
            stringResource(Res.string.set_line, formatKg(bellKg), reps)

        added -> stringResource(Res.string.set_line_added, formatKg(bellKg), reps)

        band != null -> stringResource(Res.string.set_line_band, bandName(band), reps)

        else -> stringResource(Res.string.set_line_reps, reps)
    }
    return if (side == null) line else stringResource(Res.string.common_joined, line, sideName(side))
}

/**
 * The plates [totalKg] takes a side of the user's [bar]: "20 kg bar · 20 + 1.25 a side", or that it is empty,
 * lighter than the bar or can't be made; with no bar, where to set one.
 */
@Composable
fun platesLine(totalKg: Double, bar: Double?): String {
    if (bar == null) return stringResource(Res.string.log_no_bar)
    val barKg = formatKg(bar)
    val plates = platesPerSide(totalKg, bar)
    return when {
        totalKg < bar -> stringResource(Res.string.bar_too_light, barKg)

        plates == null -> stringResource(Res.string.bar_unloadable, barKg)

        plates.isEmpty() -> stringResource(Res.string.bar_empty, barKg)

        else -> {
            var side = formatKg(plates.first())
            for (plate in plates.drop(1)) side = stringResource(Res.string.plates_plus, side, formatKg(plate))
            stringResource(Res.string.bar_plates, barKg, side)
        }
    }
}

/** A band by the name the user gave it, or one they started with by the name its colour goes by: "Medium". */
@Composable
fun bandName(band: Band): String = band.name.ifBlank {
    band.level?.let { levelName(it) }
        ?: stringResource(Res.string.band_unnamed)
}

@Composable
private fun levelName(band: BandLevel): String = stringResource(
    when (band) {
        BandLevel.ExtraLight -> Res.string.band_extra_light
        BandLevel.Light -> Res.string.band_light
        BandLevel.Medium -> Res.string.band_medium
        BandLevel.Heavy -> Res.string.band_heavy
        BandLevel.ExtraHeavy -> Res.string.band_extra_heavy
    },
)

@Composable
fun equipmentName(equipment: Equipment): String = stringResource(
    when (equipment) {
        Equipment.Kettlebell -> Res.string.equipment_kettlebell
        Equipment.Barbell -> Res.string.equipment_barbell
        Equipment.Bodyweight -> Res.string.equipment_bodyweight
        Equipment.Band -> Res.string.equipment_band
        Equipment.Mat -> Res.string.equipment_mat
    },
)

@Composable
fun focusName(focus: Focus): String = stringResource(
    when (focus) {
        Focus.UpperBody -> Res.string.focus_upper_body
        Focus.LowerBody -> Res.string.focus_lower_body
        Focus.Core -> Res.string.focus_core
        Focus.FullBody -> Res.string.focus_full_body
    },
)

@Composable
fun measureName(measure: Measure): String = stringResource(
    when (measure) {
        Measure.Reps -> Res.string.measure_reps
        Measure.Time -> Res.string.measure_time
    },
)

/** An exercise's facts in a line: "Bodyweight · Time · a side · Core". */
@Composable
fun exerciseFacts(exercise: Exercise): String = listOfNotNull(
    equipmentName(exercise.equipment),
    measureName(exercise.measure),
    if (exercise.perSide) stringResource(Res.string.exercise_a_side) else null,
    focusName(exercise.focus),
).reduce { line, fact -> stringResource(Res.string.common_joined, line, fact) }

@Composable
fun bandColourName(colour: BandColour): String = stringResource(
    when (colour) {
        BandColour.Yellow -> Res.string.colour_yellow
        BandColour.Red -> Res.string.colour_red
        BandColour.Green -> Res.string.colour_green
        BandColour.Blue -> Res.string.colour_blue
        BandColour.Black -> Res.string.colour_black
        BandColour.Purple -> Res.string.colour_purple
        BandColour.Orange -> Res.string.colour_orange
        BandColour.Grey -> Res.string.colour_grey
    },
)
