package dev.justinsmith.bells.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.exercise_editor_new
import dev.justinsmith.bells.resources.hold_time_it
import dev.justinsmith.bells.resources.library_title
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.log_fewer_reps
import dev.justinsmith.bells.resources.log_less_time
import dev.justinsmith.bells.resources.log_more_reps
import dev.justinsmith.bells.resources.log_more_time
import dev.justinsmith.bells.resources.next_free_title
import dev.justinsmith.bells.ui.Choice
import dev.justinsmith.bells.ui.Stepper
import dev.justinsmith.bells.ui.countLine
import dev.justinsmith.bells.ui.sideName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * In a free session: the set to log next, composed on the spot from the sheet's draft and logged from the
 * session's foot. The lift with How to and Change (the library, as a sheet), its load a tap lighter or heavier,
 * its reps (or a hold's length, with Time it), the side of a one-sided lift, Two bells when the lift can be
 * doubled, and what was lifted last time. Once logged it stays, the side flipped, for the next set.
 */
@Composable
internal fun DraftSetCard(
    state: SessionUiState,
    actions: SessionActions,
    onChange: () -> Unit,
    onTimeIt: () -> Unit,
) {
    val draft = state.draft
    val equipment = draft.exercise.equipment
    ListCard(Modifier.padding(top = Spacing.space8)) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.next_free_title),
                style = IntervalTheme.type.caption,
                color = IntervalTheme.palette.textDim,
            )
            LiftHeading(draft.exercise, draft.doubled, actions.onOpenExercise, onChange = onChange)
            LoadStep(
                draft.loadKg,
                equipment,
                state,
                actions.onStepDraftLoad,
                double = draft.doubled,
                band = draft.band.takeIf { equipment == Equipment.Band },
            )
            DraftCount(draft, actions, onTimeIt)
            DraftOptions(draft, actions)
            LastTimeLine(state.draftLastTime)
        }
    }
}

/** The side of a one-sided lift, and Two bells when the lift can be doubled, on one wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftOptions(draft: SetDraft, actions: SessionActions) {
    val sided = draft.exercise.sided(draft.doubled)
    val canDouble = draft.exercise.doubles == Doubles.Optional
    if (!sided && !canDouble) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        if (sided) {
            Side.entries.forEach { side ->
                Choice(
                    sideName(side),
                    selected = side == draft.side,
                    onClick = { actions.onDraft { it.copy(side = side) } },
                )
            }
        }
        if (canDouble) {
            Choice(
                stringResource(Res.string.lift_double),
                selected = draft.double,
                onClick = { actions.onDraft { it.copy(double = !it.double) } },
                role = Role.Checkbox,
            )
        }
    }
}

/** How much of the set: its reps a step at a time, or a hold's length, five seconds at a time, with Time it. */
@Composable
private fun DraftCount(draft: SetDraft, actions: SessionActions, onTimeIt: () -> Unit) {
    if (draft.held) {
        Stepper(
            formatClock(draft.seconds),
            fewer = stringResource(Res.string.log_less_time),
            more = stringResource(Res.string.log_more_time),
            canLower = draft.seconds > SetDraft.SECONDS_STEP,
            canRaise = draft.seconds < SetDraft.MAX_SECONDS,
            onStep = { step -> actions.onDraft { it.copy(seconds = it.seconds + step * SetDraft.SECONDS_STEP) } },
            compact = true,
        )
        SecondaryButton(stringResource(Res.string.hold_time_it), onTimeIt, Modifier.fillMaxWidth())
    } else {
        Stepper(
            countLine(draft.reps, side = null),
            fewer = stringResource(Res.string.log_fewer_reps),
            more = stringResource(Res.string.log_more_reps),
            canLower = draft.reps > 1,
            canRaise = draft.reps < SetDraft.MAX_REPS,
            onStep = { step -> actions.onDraft { it.copy(reps = it.reps + step) } },
            compact = true,
        )
    }
}

/**
 * The library over the session, to pick the next set's lift; Add starts an exercise of the user's by the name
 * searched, and the card comes back with it picked.
 */
@Composable
internal fun ExercisePickerSheet(state: SessionUiState, actions: SessionActions, onDismiss: () -> Unit) {
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    IntervalSheet(
        stringResource(if (adding == null) Res.string.library_title else Res.string.exercise_editor_new),
        onDismiss,
    ) { hide ->
        ExerciseChoice(state, actions, adding, onPick = hide, onAdd = { adding = it }, onSave = hide)
    }
}
