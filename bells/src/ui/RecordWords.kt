package dev.justinsmith.bells.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.record_added_kg
import dev.justinsmith.bells.resources.record_heaviest
import dev.justinsmith.bells.resources.record_longest
import dev.justinsmith.bells.resources.record_most_added
import dev.justinsmith.bells.resources.record_most_reps
import dev.justinsmith.bells.resources.record_most_reps_at
import dev.justinsmith.bells.resources.record_most_reps_none_added
import dev.justinsmith.bells.resources.record_most_reps_with
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** A record as a row of a list: the lift's name over [recordLine]. */
@Composable
fun RecordRow(record: PersonalRecord, modifier: Modifier = Modifier, tagged: Boolean = false) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(Spacing.space16)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Text(liftName(record.exercise, record.double), style = IntervalTheme.type.label, color = palette.text)
            if (tagged) RecordTag()
        }
        Text(recordLine(record), style = IntervalTheme.type.meta, color = palette.textDim)
    }
}

/**
 * A record in words, in the terms its sets are counted: "Heaviest 20 kg × 5 · most reps 12 at 12 kg",
 * "Most added +8 kg × 6", "Heaviest Heavy × 10 · most reps 20 with Light", "Most reps 20", "Longest 1:30".
 * The most reps are told apart only when they were not done at the heaviest.
 */
@Composable
fun recordLine(record: PersonalRecord): String = when (record) {
    is PersonalRecord.Held -> stringResource(Res.string.record_longest, formatClock(record.longestSeconds))

    is PersonalRecord.Banded -> {
        val heaviest = stringResource(Res.string.record_heaviest, bandName(record.heaviest), record.repsAtHeaviest)
        if (record.mostReps > record.repsAtHeaviest) {
            val most = stringResource(Res.string.record_most_reps_with, record.mostReps, bandName(record.mostRepsBand))
            stringResource(Res.string.common_joined, heaviest, most)
        } else {
            heaviest
        }
    }

    is PersonalRecord.Weighted -> weightedLine(record)
}

@Composable
private fun weightedLine(record: PersonalRecord.Weighted): String {
    if (record.heaviestKg == 0.0) return stringResource(Res.string.record_most_reps, record.mostReps)
    val added = record.exercise.equipment == Equipment.Bodyweight
    val heaviest = if (added) {
        stringResource(Res.string.record_most_added, formatKg(record.heaviestKg), record.repsAtHeaviest)
    } else {
        stringResource(
            Res.string.record_heaviest,
            stringResource(Res.string.kg_value, formatKg(record.heaviestKg)),
            record.repsAtHeaviest,
        )
    }
    if (record.mostReps <= record.repsAtHeaviest) return heaviest
    val most = when {
        record.mostRepsKg == 0.0 -> stringResource(Res.string.record_most_reps_none_added, record.mostReps)

        added -> stringResource(
            Res.string.record_most_reps_at,
            record.mostReps,
            stringResource(Res.string.record_added_kg, formatKg(record.mostRepsKg)),
        )

        else -> stringResource(
            Res.string.record_most_reps_at,
            record.mostReps,
            stringResource(Res.string.kg_value, formatKg(record.mostRepsKg)),
        )
    }
    return stringResource(Res.string.common_joined, heaviest, most)
}
