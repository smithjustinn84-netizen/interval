package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.flow_move_time_sides
import org.jetbrains.compose.resources.stringResource

/**
 * A flow's moves in order on a sheet's card, each with its time ("0:30 a side") and how it is done: the Mobility
 * pillar's sheet and Today's look before Start both show them.
 */
@Composable
fun FlowMoves(moves: List<Move>, modifier: Modifier = Modifier) {
    ListCard(modifier, fill = IntervalTheme.palette.surfaceVariant) {
        moves.forEachIndexed { index, move ->
            if (index > 0) RowDivider()
            MoveRow(move)
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
                if (move.sided) stringResource(Res.string.flow_move_time_sides, time) else time,
                style = IntervalTheme.type.label,
                color = palette.textDim,
            )
        }
        Text(move.cue, style = IntervalTheme.type.meta, color = palette.textDim)
    }
}
