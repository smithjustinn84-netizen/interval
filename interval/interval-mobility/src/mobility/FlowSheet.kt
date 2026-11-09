package dev.justinsmith.interval.mobility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.mobility.resources.Res
import dev.justinsmith.interval.mobility.resources.mobility_edit
import dev.justinsmith.interval.mobility.resources.mobility_kind
import dev.justinsmith.interval.mobility.resources.mobility_make_yours
import dev.justinsmith.interval.mobility.resources.mobility_prep_line
import dev.justinsmith.interval.mobility.resources.mobility_sheet_note
import dev.justinsmith.interval.mobility.resources.mobility_start
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.FlowMoves
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MoveDetail
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.movesText
import org.jetbrains.compose.resources.stringResource

/**
 * One flow before it starts: how long it takes, then its moves in order, each with its time ("0:30 a side") and
 * how it is done, so none is met for the first time on the floor; a move tapped shows its page in the sheet's place
 * ([MoveDetail]: its words, and Lift's figure doing it where it is one of Lift's exercises). Start, over them, runs
 * it; under them, [onChange] leads to a copy of a shipped flow ("Make it yours") or the editor of one of the user's
 * own ("Edit").
 */
@Composable
internal fun FlowSheet(
    item: FlowItem,
    prepSeconds: Int,
    onStart: () -> Unit,
    onChange: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val flow = item.flow
    IntervalSheet(title = flow.name, onDismiss = onDismiss) {
        // A move tapped takes the sheet over with its page, Back under it; the sheet's own ways out still close it.
        var openMove by rememberSaveable { mutableStateOf<Int?>(null) }
        val open = openMove
        if (open != null && open in flow.moves.indices) {
            MoveDetail(flow.moves[open], number = open + 1, of = flow.moves.size, onBack = { openMove = null })
            return@IntervalSheet
        }
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
        FlowMoves(flow.moves, onOpen = { openMove = it })
        QuietTextButton(
            stringResource(if (flow.custom) Res.string.mobility_edit else Res.string.mobility_make_yours),
            onChange,
            Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
