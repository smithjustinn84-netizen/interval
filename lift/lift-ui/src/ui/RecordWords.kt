package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.common_joined
import dev.justinsmith.lift.ui.resources.kg_value
import dev.justinsmith.lift.ui.resources.record_added_kg
import dev.justinsmith.lift.ui.resources.record_heaviest
import dev.justinsmith.lift.ui.resources.record_heaviest_held
import dev.justinsmith.lift.ui.resources.record_longest
import dev.justinsmith.lift.ui.resources.record_longest_at
import dev.justinsmith.lift.ui.resources.record_longest_none_added
import dev.justinsmith.lift.ui.resources.record_most_added
import dev.justinsmith.lift.ui.resources.record_most_added_held
import dev.justinsmith.lift.ui.resources.record_most_reps
import dev.justinsmith.lift.ui.resources.record_most_reps_at
import dev.justinsmith.lift.ui.resources.record_most_reps_none_added
import dev.justinsmith.lift.ui.resources.record_most_reps_with
import org.jetbrains.compose.resources.stringResource

/**
 * A record as a row of a list: the lift's name over [recordLine]; without [named], for a page that is the lift's
 * own, the line alone.
 */
@Composable
fun RecordRow(record: PersonalRecord, modifier: Modifier = Modifier, tagged: Boolean = false, named: Boolean = true) {
    val palette = IntervalTheme.palette
    Column(modifier.semantics(mergeDescendants = true) {}.padding(Spacing.space16)) {
        if (!named) {
            Text(recordLine(record), style = IntervalTheme.type.body, color = palette.text)
            return@Column
        }
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
 * "Most added +8 kg × 6", "Heaviest Heavy × 10 · most reps 20 with Light", "Most reps 20", "Longest 1:30",
 * "Heaviest 24 kg · 0:30 · longest 1:00 at 16 kg". The most reps or longest hold are told apart only when they
 * were not done at the heaviest.
 */
@Composable
fun recordLine(record: PersonalRecord): String = when (record) {
    is PersonalRecord.Held -> heldLine(record)

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
private fun heldLine(record: PersonalRecord.Held): String {
    if (record.heaviestKg == 0.0) return stringResource(Res.string.record_longest, formatClock(record.longestSeconds))
    val added = record.exercise.equipment == Equipment.Bodyweight
    val atHeaviest = formatClock(record.secondsAtHeaviest)
    val heaviest = if (added) {
        stringResource(Res.string.record_most_added_held, formatKg(record.heaviestKg), atHeaviest)
    } else {
        stringResource(
            Res.string.record_heaviest_held,
            stringResource(Res.string.kg_value, formatKg(record.heaviestKg)),
            atHeaviest,
        )
    }
    if (record.longestSeconds <= record.secondsAtHeaviest) return heaviest
    val longestTime = formatClock(record.longestSeconds)
    val longest = when {
        record.longestKg == 0.0 -> stringResource(Res.string.record_longest_none_added, longestTime)

        added -> stringResource(
            Res.string.record_longest_at,
            longestTime,
            stringResource(Res.string.record_added_kg, formatKg(record.longestKg)),
        )

        else -> stringResource(
            Res.string.record_longest_at,
            longestTime,
            stringResource(Res.string.kg_value, formatKg(record.longestKg)),
        )
    }
    return stringResource(Res.string.common_joined, heaviest, longest)
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
