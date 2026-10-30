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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.MAX_EXERCISE_CUES
import dev.justinsmith.lift.domain.MAX_EXERCISE_NAME
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.canSave
import dev.justinsmith.lift.domain.exerciseNameKey
import dev.justinsmith.lift.domain.takenNames
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * The exercise being added or changed: whether it is saved already (and archived), the names of the user's
 * others it can't take, and whether sets of it are logged, which fixes its equipment and measure.
 */
data class ExerciseEditorUiState(
    val draft: ExerciseDraft = ExerciseDraft(),
    val saved: Boolean = false,
    val archived: Boolean = false,
    val taken: Set<String> = emptySet(),
    val locked: Boolean = false,
)

/**
 * The exercise a sheet is adding, named [openedName] to start (from a search that found nothing), or changing,
 * archiving or restoring: the user's [openedUid]; one gone meanwhile is added afresh. It belongs to the screen
 * (see [rememberExerciseEditor]), so it lives through a configuration change, and its draft through process
 * death in [savedState]; a sheet opened again calls [reopen], to start from what is saved rather than from a
 * draft left behind. The names taken and whether sets of it are logged follow the repository while it is open.
 */
class ExerciseEditorViewModel(
    private val repository: LiftRepository,
    private val savedState: SavedStateHandle,
    private val openedUid: String?,
    private val openedName: String,
) : ViewModel() {
    // Read with the draft, which changes whenever this does: [reopen] sets both.
    private var uid = savedUid()

    private val draft = MutableStateFlow(savedState.restoredDraft() ?: savedDraft())

    val state: StateFlow<ExerciseEditorUiState> =
        combine(draft, repository.customExercises, repository.sessions, repository.active) {
                draft,
                made,
                sessions,
                active,
            ->
            editorState(draft, made, sessions.flatMap { it.sets } + active?.sets.orEmpty())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            editorState(
                draft.value,
                repository.customExercises.value,
                repository.sessions.value.flatMap { it.sets } + repository.active.value?.sets.orEmpty(),
            ),
        )

    private fun editorState(
        draft: ExerciseDraft,
        made: List<CustomExercise>,
        sets: List<SetLog>,
    ): ExerciseEditorUiState {
        val existing = made.firstOrNull { it.uid == uid }
        return ExerciseEditorUiState(
            draft = draft,
            saved = existing != null,
            archived = existing?.archived == true,
            taken = takenNames(made, uid),
            locked = existing != null && sets.any { it.exercise.key == existing.key },
        )
    }

    /** Starts again from the exercise as saved, or the name it was opened with, leaving any draft behind. */
    fun reopen() {
        uid = savedUid()
        draft.value = savedDraft()
        savedState.keep(draft.value)
    }

    private fun savedUid() = openedUid?.let { id -> repository.customExercises.value.firstOrNull { it.uid == id } }?.uid

    private fun savedDraft() = repository.customExercises.value.firstOrNull { it.uid == uid }
        ?.let { ExerciseDraft(it.name, it.equipment, it.measure, it.perSide, it.focus, it.cues) }
        ?: ExerciseDraft(name = openedName.trim())

    /** Changes the draft; a locked one keeps its equipment and measure. */
    fun change(transform: (ExerciseDraft) -> ExerciseDraft) {
        val current = state.value
        val changed = transform(current.draft)
        draft.value = if (current.locked) {
            changed.copy(equipment = current.draft.equipment, measure = current.draft.measure)
        } else {
            changed
        }
        savedState.keep(draft.value)
    }

    /** Saves it as [name] with [cues]; false, and nothing saved, while it can't be. */
    fun save(name: String, cues: String): Boolean {
        val current = state.value
        if (!current.draft.canSave(name, current.taken)) return false
        repository.saveExercise(uid, current.draft.copy(name = name, cues = cues))
        return true
    }

    /** Archives the exercise, or restores an archived one; nothing for one not saved yet. */
    fun toggleArchived() {
        val uid = uid ?: return
        repository.setArchived(uid, !state.value.archived)
    }
}

// The draft as saved state holds it, enums by their names: not every platform saves an enum.
private fun SavedStateHandle.keep(draft: ExerciseDraft) {
    this[NAME] = draft.name
    this[EQUIPMENT] = draft.equipment.name
    this[MEASURE] = draft.measure.name
    this[PER_SIDE] = draft.perSide
    this[FOCUS] = draft.focus.name
    this[CUES] = draft.cues
}

// Every field is kept together, so a draft without its name was never kept.
private fun SavedStateHandle.restoredDraft(): ExerciseDraft? {
    val name = get<String>(NAME) ?: return null
    val blank = ExerciseDraft()
    return ExerciseDraft(
        name = name,
        equipment = Equipment.entries.firstOrNull { it.name == get<String>(EQUIPMENT) } ?: blank.equipment,
        measure = Measure.entries.firstOrNull { it.name == get<String>(MEASURE) } ?: blank.measure,
        perSide = get<Boolean>(PER_SIDE) ?: blank.perSide,
        focus = Focus.entries.firstOrNull { it.name == get<String>(FOCUS) } ?: blank.focus,
        cues = get<String>(CUES) ?: blank.cues,
    )
}

private const val NAME = "draft_name"
private const val EQUIPMENT = "draft_equipment"
private const val MEASURE = "draft_measure"
private const val PER_SIDE = "draft_per_side"
private const val FOCUS = "draft_focus"
private const val CUES = "draft_cues"

/**
 * The editor for the user's exercise [uid], or a new one named [name], for as long as the caller shows it. The
 * screen keeps it, one for each exercise opened, as it keeps its other ViewModels; shown anew (not brought back
 * after a configuration change or process death), it starts again from what is saved.
 */
@Composable
fun rememberExerciseEditor(uid: String?, name: String): ExerciseEditorViewModel {
    val scope = currentKoinScope()
    val editor = viewModel(key = "exerciseEditor:${uid.orEmpty()}:$name") {
        ExerciseEditorViewModel(scope.get(), createSavedStateHandle(), uid, name)
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
