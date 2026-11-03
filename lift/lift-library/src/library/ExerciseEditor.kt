package dev.justinsmith.lift.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.MAX_EXERCISE_CUES
import dev.justinsmith.lift.domain.MAX_EXERCISE_NAME
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.canSave
import dev.justinsmith.lift.domain.exerciseNameKey
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.exercise_archive
import dev.justinsmith.lift.library.resources.exercise_archive_hint
import dev.justinsmith.lift.library.resources.exercise_archived_note
import dev.justinsmith.lift.library.resources.exercise_cues
import dev.justinsmith.lift.library.resources.exercise_cues_placeholder
import dev.justinsmith.lift.library.resources.exercise_editor_new
import dev.justinsmith.lift.library.resources.exercise_equipment
import dev.justinsmith.lift.library.resources.exercise_locked
import dev.justinsmith.lift.library.resources.exercise_name
import dev.justinsmith.lift.library.resources.exercise_name_needed
import dev.justinsmith.lift.library.resources.exercise_name_placeholder
import dev.justinsmith.lift.library.resources.exercise_name_taken
import dev.justinsmith.lift.library.resources.exercise_one_side
import dev.justinsmith.lift.library.resources.exercise_one_side_hint
import dev.justinsmith.lift.library.resources.exercise_restore
import dev.justinsmith.lift.library.resources.exercise_save
import dev.justinsmith.lift.library.resources.library_focus
import dev.justinsmith.lift.library.resources.library_measured
import dev.justinsmith.lift.library.resources.notice_exercise_saved
import dev.justinsmith.lift.ui.ChoiceRow
import dev.justinsmith.lift.ui.equipmentName
import dev.justinsmith.lift.ui.focusName
import dev.justinsmith.lift.ui.measureName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The editor for the user's exercise [uid], or a new one named [name], for as long as the caller shows it. The
 * screen keeps it, one for each exercise opened, as it keeps its other ViewModels; shown anew (not brought back
 * after a configuration change or process death), it starts again from what is saved.
 */
@Composable
fun rememberExerciseEditor(uid: String?, name: String): ExerciseEditorViewModel {
    val editor = koinViewModel<ExerciseEditorViewModel>(key = "exerciseEditor:${uid.orEmpty()}:$name") {
        parametersOf(uid, name)
    }
    // Runs only when the sheet is shown anew: brought back, the saveable is restored and this is skipped.
    rememberSaveable(uid, name) {
        editor.reopen()
        true
    }
    return editor
}

/**
 * The sheet that adds an exercise of the user's, named [name] to start: beside the page on a tablet, rising from
 * the bottom on a phone, as a band or a preset does. Saving closes it; so does every other way out, which saves
 * nothing. One already saved is changed from its page ([ExerciseDetailSheet]).
 */
@Composable
fun ExerciseEditorSheet(name: String, onDismiss: () -> Unit) {
    val editor = rememberExerciseEditor(uid = null, name = name)
    val state by editor.state.collectAsStateWithLifecycle()
    IntervalSheet(stringResource(Res.string.exercise_editor_new), onDismiss = onDismiss) { hide ->
        ExerciseEditorForm(
            state,
            onChange = editor::change,
            onSave = { typed, cues -> if (editor.save(typed, cues)) hide() },
            modifier = Modifier.padding(vertical = Spacing.space8),
        )
    }
}

/**
 * An exercise of the user's, as a sheet's form: its name, what it is done with, how it is counted and sided,
 * its focus, cues. One saved can be archived, or restored, with [onArchive].
 */
@Composable
fun ExerciseEditorForm(
    state: ExerciseEditorUiState,
    onChange: ((ExerciseDraft) -> ExerciseDraft) -> Unit,
    onSave: (name: String, cues: String) -> Unit,
    modifier: Modifier = Modifier,
    onArchive: () -> Unit = {},
) {
    val draft = state.draft
    val palette = IntervalTheme.palette
    val name = rememberTextFieldState(draft.name)
    val cues = rememberTextFieldState(draft.cues)
    val typed = name.text.toString()
    val typedCues = cues.text.toString()
    val currentOnChange by rememberUpdatedState(onChange)
    val currentDraft by rememberUpdatedState(draft)
    LaunchedEffect(typed) {
        if (typed != currentDraft.name) currentOnChange { it.copy(name = typed) }
    }
    LaunchedEffect(typedCues) {
        if (typedCues != currentDraft.cues) currentOnChange { it.copy(cues = typedCues) }
    }
    val taken = exerciseNameKey(typed) in state.taken
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        if (state.archived) {
            Text(
                stringResource(Res.string.exercise_archived_note),
                style = IntervalTheme.type.meta,
                color = palette.text,
            )
        }
        GroupLabel(stringResource(Res.string.exercise_name))
        NameField(
            name,
            placeholder = stringResource(Res.string.exercise_name_placeholder),
            label = stringResource(Res.string.exercise_name),
            maxLength = MAX_EXERCISE_NAME,
            capitalization = KeyboardCapitalization.Words,
            isError = taken,
        )
        if (taken) {
            Text(
                stringResource(Res.string.exercise_name_taken),
                style = IntervalTheme.type.meta,
                color = palette.danger,
            )
        }
        GroupLabel(stringResource(Res.string.exercise_equipment))
        if (state.locked) {
            Text(stringResource(Res.string.exercise_locked), style = IntervalTheme.type.meta, color = palette.textDim)
        } else {
            ChoiceRow(Equipment.entries, draft.equipment, { equipmentName(it) }, { e ->
                onChange { it.copy(equipment = e) }
            })
            GroupLabel(stringResource(Res.string.library_measured))
            ChoiceRow(Measure.entries, draft.measure, { measureName(it) }, { m -> onChange { it.copy(measure = m) } })
        }
        ListCard(Modifier.padding(top = Spacing.space8)) {
            ToggleRow(
                stringResource(Res.string.exercise_one_side),
                stringResource(Res.string.exercise_one_side_hint),
                checked = draft.perSide,
                onToggle = { onChange { it.copy(perSide = !it.perSide) } },
            )
        }
        GroupLabel(stringResource(Res.string.library_focus))
        ChoiceRow(Focus.entries, draft.focus, { focusName(it) }, { f -> onChange { it.copy(focus = f) } })
        GroupLabel(stringResource(Res.string.exercise_cues))
        NameField(
            cues,
            placeholder = stringResource(Res.string.exercise_cues_placeholder),
            label = stringResource(Res.string.exercise_cues),
            maxLength = MAX_EXERCISE_CUES,
            multiLine = true,
        )
        SaveAction(
            canSave = draft.canSave(typed, state.taken),
            needsName = typed.isBlank(),
            name = typed.trim(),
            onSave = { onSave(it, typedCues) },
        )
        if (state.saved) ArchiveControls(state.archived, onArchive)
    }
}

@Composable
private fun ArchiveControls(archived: Boolean, onArchive: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        SecondaryButton(
            stringResource(if (archived) Res.string.exercise_restore else Res.string.exercise_archive),
            onArchive,
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
        )
        if (!archived) {
            Text(
                stringResource(Res.string.exercise_archive_hint),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
    }
}

/**
 * Save, enabled once there is a unique name to save under, with an inline hint while blank; it says what it
 * saved as it goes.
 */
@Composable
private fun SaveAction(
    canSave: Boolean,
    needsName: Boolean,
    name: String,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notices = LocalNotices.current
    val saved = stringResource(Res.string.notice_exercise_saved, name)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        AccentButton(
            stringResource(Res.string.exercise_save),
            {
                notices.show(saved)
                onSave(name)
            },
            Modifier.fillMaxWidth().padding(top = Spacing.space16),
            enabled = canSave,
        )
        if (needsName) {
            Text(
                stringResource(Res.string.exercise_name_needed),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
    }
}
