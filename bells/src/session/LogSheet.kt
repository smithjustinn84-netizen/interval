package dev.justinsmith.bells.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.library.ExercisePicker
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.emom_fewer_minutes
import dev.justinsmith.bells.resources.emom_minutes
import dev.justinsmith.bells.resources.emom_more_minutes
import dev.justinsmith.bells.resources.emom_start
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.log_added
import dev.justinsmith.bells.resources.log_added_none
import dev.justinsmith.bells.resources.log_band
import dev.justinsmith.bells.resources.log_bell
import dev.justinsmith.bells.resources.log_change
import dev.justinsmith.bells.resources.log_exercise
import dev.justinsmith.bells.resources.log_fewer_reps
import dev.justinsmith.bells.resources.log_first_side
import dev.justinsmith.bells.resources.log_held
import dev.justinsmith.bells.resources.log_kind_emom
import dev.justinsmith.bells.resources.log_kind_set
import dev.justinsmith.bells.resources.log_less_time
import dev.justinsmith.bells.resources.log_more_reps
import dev.justinsmith.bells.resources.log_more_time
import dev.justinsmith.bells.resources.log_no_bells
import dev.justinsmith.bells.resources.log_reps
import dev.justinsmith.bells.resources.log_reps_each_minute
import dev.justinsmith.bells.resources.log_side
import dev.justinsmith.bells.resources.log_title
import dev.justinsmith.bells.resources.session_log
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.bandName
import dev.justinsmith.bells.ui.exerciseFacts
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.bells.ui.sideName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.stringResource

/** What the Log sheet adds: one set now, or an EMOM of the set every minute. */
private enum class LogKind { Set, Emom }

/**
 * Pick a set or an EMOM, then what it is done with and the exercise, its load (a bell or two, weight added
 * to bodyweight, a band, or none on the mat) and its reps, or for a hold how long; for a set, the side of a
 * one-sided exercise, for an EMOM its minutes and first side. A hold is only ever a set. Then log the set, or
 * start the EMOM.
 */
@Composable
internal fun LogSetSheet(state: SessionUiState, actions: SessionActions, onDismiss: () -> Unit) {
    val draft = state.draft
    // Saved by name: an enum is not saveable on every platform.
    var kindName by rememberSaveable { mutableStateOf(LogKind.Set.name) }
    val kind = if (draft.held) LogKind.Set else LogKind.valueOf(kindName)
    // Change swaps the sheet for the library until an exercise is picked.
    var picking by rememberSaveable { mutableStateOf(false) }
    IntervalSheet(stringResource(Res.string.log_title), onDismiss = onDismiss) { hide ->
        if (picking) {
            ExercisePicker(
                state.library,
                actions.onFilters,
                Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
                selected = draft.exercise,
                onPick = { e ->
                    actions.onDraft { it.copy(exercise = e) }
                    picking = false
                },
            )
            return@IntervalSheet
        }
        Column(
            Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            if (!draft.held) {
                SegmentedPicker(
                    LogKind.entries,
                    kind,
                    label = {
                        stringResource(if (it == LogKind.Set) Res.string.log_kind_set else Res.string.log_kind_emom)
                    },
                    onSelect = { kindName = it.name },
                    modifier = Modifier.padding(bottom = Spacing.space8),
                )
            }
            ExerciseCard(draft.exercise, onChange = { picking = true })
            LoadPicker(state, actions)
            MeasurePicker(draft, emom = kind == LogKind.Emom, actions)
            if (kind == LogKind.Emom) {
                GroupLabel(stringResource(Res.string.emom_minutes))
                CountStepper(
                    draft.minutes,
                    fewer = stringResource(Res.string.emom_fewer_minutes),
                    more = stringResource(Res.string.emom_more_minutes),
                    max = SetDraft.MAX_MINUTES,
                    onStep = { step -> actions.onDraft { it.copy(minutes = it.minutes + step) } },
                )
            }
            if (draft.exercise.sided(draft.doubled)) {
                GroupLabel(
                    stringResource(if (kind == LogKind.Set) Res.string.log_side else Res.string.log_first_side),
                )
                ChoiceRow(Side.entries, draft.side, {
                    sideName(it)
                }, { side -> actions.onDraft { it.copy(side = side) } })
            }
            AccentButton(
                stringResource(if (kind == LogKind.Set) Res.string.session_log else Res.string.emom_start),
                onClick = {
                    if (kind == LogKind.Set) actions.onLog() else actions.onStartEmom()
                    hide()
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.space16, bottom = Spacing.space8),
            )
        }
    }
}

/** The exercise the set is of, its facts under its name, and Change to pick another from the library. */
@Composable
private fun ExerciseCard(exercise: Exercise, onChange: () -> Unit) {
    val palette = IntervalTheme.palette
    ListCard {
        Row(
            Modifier.padding(
                start = Spacing.space16,
                end = Spacing.space4,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.log_exercise),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(exerciseName(exercise), style = IntervalTheme.type.section, color = palette.text)
                Text(exerciseFacts(exercise), style = IntervalTheme.type.meta, color = palette.textDim)
            }
            QuietTextButton(stringResource(Res.string.log_change), onChange)
        }
    }
}

/** How much of it: the reps (each minute, for an EMOM), or for a hold how long, in steps of five seconds. */
@Composable
private fun MeasurePicker(draft: SetDraft, emom: Boolean, actions: SessionActions) {
    if (draft.held) {
        GroupLabel(stringResource(Res.string.log_held))
        Stepper(
            formatClock(draft.seconds),
            fewer = stringResource(Res.string.log_less_time),
            more = stringResource(Res.string.log_more_time),
            canLower = draft.seconds > SetDraft.SECONDS_STEP,
            canRaise = draft.seconds < SetDraft.MAX_SECONDS,
            onStep = { step ->
                actions.onDraft { it.copy(seconds = it.seconds + step * SetDraft.SECONDS_STEP) }
            },
        )
    } else {
        GroupLabel(
            stringResource(if (!emom) Res.string.log_reps else Res.string.log_reps_each_minute),
        )
        CountStepper(
            draft.reps,
            fewer = stringResource(Res.string.log_fewer_reps),
            more = stringResource(Res.string.log_more_reps),
            max = SetDraft.MAX_REPS,
            onStep = { step -> actions.onDraft { it.copy(reps = it.reps + step) } },
        )
    }
}

/** The load the exercise takes: a bell (or two), weight added to bodyweight, a band, or nothing on the mat. */
@Composable
private fun LoadPicker(state: SessionUiState, actions: SessionActions) {
    val draft = state.draft
    when (draft.exercise.equipment) {
        Equipment.Kettlebell -> {
            GroupLabel(stringResource(Res.string.log_bell))
            ChoiceRow(state.bells, draft.bellKg, { formatKg(it) }, { kg -> actions.onDraft { it.copy(bellKg = kg) } })
            if (!state.ownsBells) {
                Text(
                    stringResource(Res.string.log_no_bells),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.palette.textDim,
                )
            }
            if (draft.exercise.doubles == Doubles.Optional) {
                ListCard {
                    ToggleRow(
                        stringResource(Res.string.lift_double),
                        null,
                        checked = draft.double,
                        onToggle = { actions.onDraft { it.copy(double = !it.double) } },
                    )
                }
            }
        }

        Equipment.Bodyweight -> {
            GroupLabel(stringResource(Res.string.log_added))
            val none = stringResource(Res.string.log_added_none)
            ChoiceRow(
                listOf(0.0) + state.bells,
                draft.addedKg,
                { if (it == 0.0) none else formatKg(it) },
                { kg -> actions.onDraft { it.copy(addedKg = kg) } },
            )
        }

        Equipment.Band -> {
            GroupLabel(stringResource(Res.string.log_band))
            ChoiceRow(BandLevel.entries, draft.band, { bandName(it) }, { b -> actions.onDraft { it.copy(band = b) } })
        }

        Equipment.Mat -> Unit
    }
}

/** A count from 1 to [max], large, with − and + that repeat while held. */
@Composable
private fun CountStepper(value: Int, fewer: String, more: String, max: Int, onStep: (Int) -> Unit) {
    Stepper(value.toString(), fewer, more, canLower = value > 1, canRaise = value < max, onStep = onStep)
}

/** A value, large, with − and + that repeat while held, each a step down or up. */
@Composable
private fun Stepper(
    value: String,
    fewer: String,
    more: String,
    canLower: Boolean,
    canRaise: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            value,
            style = IntervalTheme.type.display,
            color = IntervalTheme.palette.text,
            modifier = Modifier.weight(1f),
        )
        RepeatButton("−", fewer, enabled = canLower, onStep = { onStep(-1) })
        RepeatButton("+", more, enabled = canRaise, onStep = { onStep(1) })
    }
}
