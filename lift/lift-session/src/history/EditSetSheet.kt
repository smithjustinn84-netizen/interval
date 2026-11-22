package dev.justinsmith.lift.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SheetColumns
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.sheetIsSideways
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.session.ExerciseCard
import dev.justinsmith.lift.session.SetDraft
import dev.justinsmith.lift.session.appliedTo
import dev.justinsmith.lift.session.clamped
import dev.justinsmith.lift.session.from
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.log_edit_remove
import dev.justinsmith.lift.session.resources.log_edit_save
import dev.justinsmith.lift.session.resources.log_edit_title
import dev.justinsmith.lift.session.resources.log_fewer_reps
import dev.justinsmith.lift.session.resources.log_held
import dev.justinsmith.lift.session.resources.log_less_time
import dev.justinsmith.lift.session.resources.log_more_reps
import dev.justinsmith.lift.session.resources.log_more_time
import dev.justinsmith.lift.session.resources.log_reps
import dev.justinsmith.lift.session.resources.log_side
import dev.justinsmith.lift.ui.ChoiceRow
import dev.justinsmith.lift.ui.LoadPicker
import dev.justinsmith.lift.ui.Stepper
import dev.justinsmith.lift.ui.sideName
import org.jetbrains.compose.resources.stringResource

/**
 * Sheet for correcting a logged [set] on a finished session's detail page: its load, reps (or seconds held),
 * and side. When [canRemove] is true (the session has more than one set), offers removing the set.
 */
@Composable
internal fun EditSetSheet(
    set: SetLog,
    state: LiftDetailUiState,
    canRemove: Boolean,
    onSave: (SetLog) -> Unit,
    onRemove: (SetLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(set.id) { mutableStateOf(SetDraft().from(set, flipSide = false).clamped(state.bar)) }
    val sideways = sheetIsSideways()
    IntervalSheet(
        title = stringResource(Res.string.log_edit_title),
        onDismiss = onDismiss,
    ) { hide ->
        val what: @Composable ColumnScope.() -> Unit = {
            ExerciseCard(draft.exercise, onChange = null)
            LoadPicker(
                exercise = draft.exercise,
                double = draft.double,
                kg = when (draft.exercise.equipment) {
                    Equipment.Bodyweight -> draft.addedKg
                    Equipment.Barbell -> draft.barKg
                    else -> draft.bellKg
                },
                band = draft.band,
                bells = state.bells,
                bands = state.bands,
                onKg = { kg ->
                    kg?.let { picked ->
                        draft = when (draft.exercise.equipment) {
                            Equipment.Bodyweight -> draft.copy(addedKg = picked)
                            Equipment.Barbell -> draft.copy(barKg = picked)
                            else -> draft.copy(bellKg = picked)
                        }.clamped(state.bar)
                    }
                },
                onBand = { band -> band?.let { draft = draft.copy(band = it) } },
                onDouble = { draft = draft.copy(double = !draft.double) },
                ownsBells = state.ownsBells,
                bar = state.bar,
            )
        }
        val howMuch: @Composable ColumnScope.() -> Unit = {
            EditMeasure(draft) { draft = it.clamped(state.bar) }
            if (draft.exercise.sided(draft.doubled)) {
                GroupLabel(stringResource(Res.string.log_side))
                ChoiceRow(Side.entries, draft.side, { sideName(it) }, { draft = draft.copy(side = it) })
            }
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.space16, bottom = Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                AccentButton(
                    stringResource(Res.string.log_edit_save),
                    onClick = {
                        onSave(draft.appliedTo(set))
                        hide()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canRemove) {
                    QuietTextButton(
                        stringResource(Res.string.log_edit_remove),
                        onClick = {
                            onRemove(set)
                            hide()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        SheetColumns(
            sideways,
            first = what,
            second = howMuch,
            modifier = Modifier.padding(vertical = Spacing.space8),
        )
    }
}

@Composable
private fun EditMeasure(draft: SetDraft, onDraft: (SetDraft) -> Unit) {
    if (draft.held) {
        GroupLabel(stringResource(Res.string.log_held))
        Stepper(
            formatClock(draft.seconds),
            fewer = stringResource(Res.string.log_less_time),
            more = stringResource(Res.string.log_more_time),
            canLower = draft.seconds > SetDraft.SECONDS_STEP,
            canRaise = draft.seconds < SetDraft.MAX_SECONDS,
            onStep = { step -> onDraft(draft.copy(seconds = draft.seconds + step * SetDraft.SECONDS_STEP)) },
        )
    } else {
        GroupLabel(stringResource(Res.string.log_reps))
        Stepper(
            draft.reps.toString(),
            fewer = stringResource(Res.string.log_fewer_reps),
            more = stringResource(Res.string.log_more_reps),
            canLower = draft.reps > 1,
            canRaise = draft.reps < SetDraft.MAX_REPS,
            onStep = { step -> onDraft(draft.copy(reps = draft.reps + step)) },
        )
    }
}
