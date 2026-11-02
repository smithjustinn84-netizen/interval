package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.ui.RecordRow
import dev.justinsmith.lift.ui.StatTile
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.kg_value
import dev.justinsmith.lift.ui.resources.session_average_heart_rate
import dev.justinsmith.lift.ui.resources.session_bpm_value
import dev.justinsmith.lift.ui.resources.session_moved
import dev.justinsmith.lift.ui.resources.session_peak_heart_rate
import dev.justinsmith.lift.ui.resources.session_time
import dev.justinsmith.lift.ui.resources.summary_records
import dev.justinsmith.lift.ui.resources.summary_reps
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session's numbers: its time and reps, the weight moved, its heart rate once read from the health
 * store, then the [records] it set, if any, each tagged when [highlight]. The summary shows them as a session
 * ends, and its page in History afterwards.
 */
@Composable
fun SessionStats(
    session: LiftSession,
    records: List<PersonalRecord>,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            StatTile(
                stringResource(Res.string.session_time),
                formatClock(session.elapsedSeconds(0)),
                Modifier.weight(1f),
            )
            StatTile(stringResource(Res.string.summary_reps), session.reps.toString(), Modifier.weight(1f))
        }
        StatTile(
            stringResource(Res.string.session_moved),
            stringResource(Res.string.kg_value, formatKg(session.tonnageKg)),
            Modifier.fillMaxWidth(),
        )
        val average = session.averageBpm
        val peak = session.peakBpm
        if (average != null && peak != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                StatTile(
                    stringResource(Res.string.session_average_heart_rate),
                    stringResource(Res.string.session_bpm_value, average),
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(Res.string.session_peak_heart_rate),
                    stringResource(Res.string.session_bpm_value, peak),
                    Modifier.weight(1f),
                )
            }
        }
        if (records.isNotEmpty()) {
            GroupLabel(stringResource(Res.string.summary_records))
            ListCard {
                records.forEachIndexed { i, record ->
                    if (i > 0) RowDivider()
                    RecordRow(record, tagged = highlight)
                }
            }
        }
    }
}
