package dev.justinsmith.interval.mobility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.mobility.resources.Res
import dev.justinsmith.interval.mobility.resources.flow_add_move
import dev.justinsmith.interval.mobility.resources.move_cue_label
import dev.justinsmith.interval.mobility.resources.move_cue_placeholder
import dev.justinsmith.interval.mobility.resources.move_done
import dev.justinsmith.interval.mobility.resources.move_down
import dev.justinsmith.interval.mobility.resources.move_held
import dev.justinsmith.interval.mobility.resources.move_held_hint
import dev.justinsmith.interval.mobility.resources.move_less_cd
import dev.justinsmith.interval.mobility.resources.move_library
import dev.justinsmith.interval.mobility.resources.move_more_cd
import dev.justinsmith.interval.mobility.resources.move_name_label
import dev.justinsmith.interval.mobility.resources.move_name_placeholder
import dev.justinsmith.interval.mobility.resources.move_own
import dev.justinsmith.interval.mobility.resources.move_remove
import dev.justinsmith.interval.mobility.resources.move_sided
import dev.justinsmith.interval.mobility.resources.move_sided_hint
import dev.justinsmith.interval.mobility.resources.move_time
import dev.justinsmith.interval.mobility.resources.move_time_side
import dev.justinsmith.interval.mobility.resources.move_title
import dev.justinsmith.interval.mobility.resources.move_type
import dev.justinsmith.interval.mobility.resources.move_type_hint
import dev.justinsmith.interval.mobility.resources.move_up
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.steppedSeconds
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.stringResource

/**
 * The moves a flow can take: the library's mat moves and holds, each by name, and one typed in. [onPick] hands over
 * the move picked, which the editor adds and opens to set its time.
 */
@Composable
internal fun AddMoveSheet(onPick: (Move) -> Unit, onDismiss: () -> Unit) {
    IntervalSheet(stringResource(Res.string.flow_add_move), onDismiss) {
        val typed = Move(name = "", seconds = Move.SECONDS_RANGE.first + 4 * Move.SECONDS_STEP, cue = "")
        GroupLabel(stringResource(Res.string.move_own))
        ListCard {
            LinkRow(stringResource(Res.string.move_type), stringResource(Res.string.move_type_hint), { onPick(typed) })
        }
        GroupLabel(stringResource(Res.string.move_library))
        ListCard {
            libraryMoves.forEachIndexed { index, exercise ->
                if (index > 0) RowDivider()
                val move = libraryMove(exercise)
                LinkRow(exerciseName(exercise), move.cue.takeIf { it.isNotEmpty() }, { onPick(move) })
            }
        }
    }
}

/** What a move's sheet can do besides change it: move it up or down the flow, or take it out. */
internal class MoveActions(
    val onUp: (() -> Unit)?,
    val onDown: (() -> Unit)?,
    val onRemove: () -> Unit,
)

/**
 * One move of the flow: its name and how it is done, its time in steps of five seconds, whether it is done a side at
 * a time and whether it is held still. Each change is handed on as it is made ([onChange]); Done closes it.
 */
@Composable
internal fun MoveSheet(move: Move, onChange: (Move) -> Unit, actions: MoveActions, onDismiss: () -> Unit) {
    IntervalSheet(move.name.ifBlank { stringResource(Res.string.move_title) }, onDismiss) { hide ->
        MoveWords(move, onChange)
        MoveTime(move, onChange)
        ListCard {
            ToggleRow(
                stringResource(Res.string.move_sided),
                stringResource(Res.string.move_sided_hint),
                move.sided,
                onToggle = { onChange(move.copy(sided = !move.sided)) },
            )
            RowDivider()
            ToggleRow(
                stringResource(Res.string.move_held),
                stringResource(Res.string.move_held_hint),
                move.held,
                onToggle = { onChange(move.copy(held = !move.held)) },
            )
        }
        AccentButton(stringResource(Res.string.move_done), hide, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            actions.onUp?.let { QuietTextButton(stringResource(Res.string.move_up), it) }
            actions.onDown?.let { QuietTextButton(stringResource(Res.string.move_down), it) }
            QuietTextButton(stringResource(Res.string.move_remove), actions.onRemove, destructive = true)
        }
    }
}

@Composable
private fun MoveWords(move: Move, onChange: (Move) -> Unit) {
    val name = rememberTextFieldState(move.name)
    val cue = rememberTextFieldState(move.cue)
    // The move as it stands when a word is typed, its time and sides included, not as the sheet opened on it.
    val current by rememberUpdatedState(move)
    val change by rememberUpdatedState(onChange)
    // Read as typed: the sheet hands each change on, so the draft and its clash follow the words.
    LaunchedEffect(name, cue) {
        snapshotFlow { name.text.toString() to cue.text.toString() }.collect { (typedName, typedCue) ->
            if (typedName != current.name || typedCue != current.cue) {
                change(current.copy(name = typedName, cue = typedCue))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        NameField(
            name,
            placeholder = stringResource(Res.string.move_name_placeholder),
            label = stringResource(Res.string.move_name_label),
            maxLength = Move.NAME_MAX_LENGTH,
            capitalization = KeyboardCapitalization.Sentences,
        )
        NameField(
            cue,
            placeholder = stringResource(Res.string.move_cue_placeholder),
            label = stringResource(Res.string.move_cue_label),
            maxLength = Move.CUE_MAX_LENGTH,
            multiLine = true,
        )
    }
}

@Composable
private fun MoveTime(move: Move, onChange: (Move) -> Unit) {
    val palette = IntervalTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        Text(
            stringResource(if (move.sided) Res.string.move_time_side else Res.string.move_time),
            style = IntervalTheme.type.label,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
        RepeatButton(
            "−",
            stringResource(Res.string.move_less_cd),
            enabled = move.seconds > Move.SECONDS_RANGE.first,
            onStep = { onChange(move.steppedSeconds(-1)) },
            modifier = Modifier.size(Sizes.segment),
        )
        Text(
            formatClock(move.seconds),
            style = IntervalTheme.type.value,
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        RepeatButton(
            "+",
            stringResource(Res.string.move_more_cd),
            enabled = move.seconds < Move.SECONDS_RANGE.last,
            onStep = { onChange(move.steppedSeconds(1)) },
            modifier = Modifier.size(Sizes.segment),
        )
    }
}
