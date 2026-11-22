package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.flow_move_time_sides
import dev.justinsmith.interval.ui.resources.move_show_how
import org.jetbrains.compose.resources.stringResource

/**
 * A flow's moves in order on a sheet's card, each with its time ("0:30 a side") and how it is done, and, for one
 * that is one of Lift's exercises, a still of its figure: the Mobility pillar's sheet and Today's look before Start
 * both show them. Given [onOpen], each row is a button that opens its move's page ([MoveDetail]) by its index.
 */
@Composable
fun FlowMoves(moves: List<Move>, modifier: Modifier = Modifier, onOpen: ((Int) -> Unit)? = null) {
    ListCard(modifier, fill = IntervalTheme.palette.surfaceVariant) {
        moves.forEachIndexed { index, move ->
            if (index > 0) RowDivider()
            MoveRow(index + 1, move, onOpen = onOpen?.let { open -> { open(index) } })
        }
    }
}

/** A move's 1-based step number in a subtle infield-green circle; decorative, as the list order already conveys it. */
@Composable
fun MoveNumberBadge(number: Int, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Box(
        modifier
            .size(IntervalSizes.stepNumber)
            .clip(CircleShape)
            .background(palette.tint(palette.restMark))
            .border(Sizes.hairline, palette.line, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = IntervalTheme.type.caption, color = palette.restMark)
    }
}

/** A move: its number, name and time on one line, how it is done under them, its figure's still at the end. */
@Composable
private fun MoveRow(number: Int, move: Move, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val tappable = if (onOpen == null) {
        Modifier
    } else {
        Modifier
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.move_show_how),
                onClick = onOpen,
            )
            .pressScale(interaction)
    }
    Row(
        modifier.fillMaxWidth().then(tappable).padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        MoveNumberBadge(number)
        Column(Modifier.weight(1f)) {
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
        move.rep?.let { MoveThumb(it) }
    }
}
