package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.band_extra_heavy
import dev.justinsmith.bells.resources.band_extra_light
import dev.justinsmith.bells.resources.band_heavy
import dev.justinsmith.bells.resources.band_light
import dev.justinsmith.bells.resources.band_medium
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.equipment_band
import dev.justinsmith.bells.resources.equipment_bodyweight
import dev.justinsmith.bells.resources.equipment_kettlebell
import dev.justinsmith.bells.resources.equipment_mat
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
 * A set in words, by what it was done with: a bell "24 kg × 10", two "2 × 16 kg × 5"; bodyweight "× 15", or
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
    band: BandLevel? = null,
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

        equipment == Equipment.Kettlebell -> stringResource(Res.string.set_line, formatKg(bellKg), reps)

        added -> stringResource(Res.string.set_line_added, formatKg(bellKg), reps)

        band != null -> stringResource(Res.string.set_line_band, bandName(band), reps)

        else -> stringResource(Res.string.set_line_reps, reps)
    }
    return if (side == null) line else stringResource(Res.string.common_joined, line, sideName(side))
}

/** A band by the name its colour usually goes by: "Medium". */
@Composable
fun bandName(band: BandLevel): String = stringResource(
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
        Equipment.Bodyweight -> Res.string.equipment_bodyweight
        Equipment.Band -> Res.string.equipment_band
        Equipment.Mat -> Res.string.equipment_mat
    },
)
