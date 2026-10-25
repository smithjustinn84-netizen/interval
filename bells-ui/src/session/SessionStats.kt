package dev.justinsmith.bells.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.ui.RecordRow
import dev.justinsmith.bells.ui.StatTile
import dev.justinsmith.bells.ui.resources.Res
import dev.justinsmith.bells.ui.resources.kg_value
import dev.justinsmith.bells.ui.resources.session_moved
import dev.justinsmith.bells.ui.resources.session_time
import dev.justinsmith.bells.ui.resources.summary_records
import dev.justinsmith.bells.ui.resources.summary_reps
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session's numbers: its time and reps, the weight moved, then the [records] it set, if any,
 * each tagged when [highlight]. The summary shows them as a session ends, and its page in History afterwards.
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
