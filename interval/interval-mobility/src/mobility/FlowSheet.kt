package dev.justinsmith.interval.mobility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.mobility.resources.Res
import dev.justinsmith.interval.mobility.resources.mobility_kind
import dev.justinsmith.interval.mobility.resources.mobility_move_time_sides
import dev.justinsmith.interval.mobility.resources.mobility_prep_line
import dev.justinsmith.interval.mobility.resources.mobility_sheet_note
import dev.justinsmith.interval.mobility.resources.mobility_start
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.movesText
import org.jetbrains.compose.resources.stringResource

/**
 * One flow before it starts: how long it takes, then its moves in order, each with its time ("0:30 a side") and
 * how it is done, so none is met for the first time on the floor. Start, over them, runs it.
 */
@Composable
internal fun FlowSheet(item: FlowItem, prepSeconds: Int, onStart: () -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    val flow = item.flow
    IntervalSheet(title = flow.name, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                joined(listOfNotNull(stringResource(Res.string.mobility_kind), movesText(flow), flow.note)),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(
                flow.name,
                style = IntervalTheme.type.value,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Text(
                item.total,
                style = IntervalTheme.type.display,
                color = palette.text,
                modifier = Modifier.alignByBaseline(),
            )
            if (prepSeconds > 0) {
                Text(
                    stringResource(Res.string.mobility_prep_line, formatClock(prepSeconds)),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        // Start before the moves: a flow of ten is a long way to scroll for it.
        StartButton(onStart, Modifier.fillMaxWidth(), label = stringResource(Res.string.mobility_start))
        Text(stringResource(Res.string.mobility_sheet_note), style = IntervalTheme.type.meta, color = palette.textDim)
        ListCard(fill = palette.surfaceVariant) {
            flow.moves.forEachIndexed { index, move ->
                if (index > 0) RowDivider()
                MoveRow(move)
            }
        }
    }
}

/** A move: its name and time on one line, how it is done under them. */
@Composable
private fun MoveRow(move: Move, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Text(move.name, style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
            val time = formatClock(move.seconds)
            Text(
                if (move.sided) stringResource(Res.string.mobility_move_time_sides, time) else time,
                style = IntervalTheme.type.label,
                color = palette.textDim,
            )
        }
        Text(move.cue, style = IntervalTheme.type.meta, color = palette.textDim)
    }
}
