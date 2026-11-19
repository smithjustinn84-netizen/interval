package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.ui.SheetList
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.library.ExerciseEditorForm
import dev.justinsmith.lift.library.rememberExerciseEditor
import dev.justinsmith.lift.library.rememberPickerList

/**
 * What the Log sheet shows in place of the set, and the Next set card's sheet shows, while the exercise is
 * being chosen: the library, as the sheet's list, until one is picked ([onPick]) or Add starts a new one by its
 * name ([onAdd]), which [NewExercise] then shows. With [closeOnPick] a pick also hides the sheet.
 */
@Composable
internal fun exerciseChoiceList(
    state: SessionUiState,
    actions: SessionActions,
    onPick: () -> Unit,
    onAdd: (name: String) -> Unit,
    closeOnPick: Boolean = false,
): SheetList = rememberPickerList(
    state.library,
    actions.onFilters,
    selected = state.draft.exercise,
    onPick = { e ->
        actions.onDraft { it.copy(exercise = e) }
        onPick()
    },
    onAdd = { name ->
        actions.onAddExercise()
        onAdd(name)
    },
    closeOnPick = closeOnPick,
)

/** The form of the exercise [adding], by the name searched, until it is saved ([onSave]) and comes back picked. */
@Composable
internal fun NewExercise(adding: String, onSave: () -> Unit) {
    val editor = rememberExerciseEditor(uid = null, name = adding)
    val editorState by editor.state.collectAsStateWithLifecycle()
    ExerciseEditorForm(
        editorState,
        onChange = editor::change,
        onSave = { typed, cues -> if (editor.save(typed, cues)) onSave() },
        modifier = Modifier.padding(vertical = Spacing.space8),
    )
}
