package dev.justinsmith.lift.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.lift.data.ExerciseStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.canSave
import dev.justinsmith.lift.domain.takenNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
 * draft left behind. The names taken and whether sets of it are logged follow the stores while it is open.
 */
class ExerciseEditorViewModel(
    private val exerciseStore: ExerciseStore,
    private val sessionStore: SessionStore,
    private val savedState: SavedStateHandle,
    private val openedUid: String?,
    private val openedName: String,
) : ViewModel() {
    // Read with the draft, which changes whenever this does: [reopen] sets both.
    private var uid = savedUid()

    private val draft = MutableStateFlow(savedState.restoredDraft() ?: savedDraft())

    val state: StateFlow<ExerciseEditorUiState> =
        combine(draft, exerciseStore.customExercises, sessionStore.sessions, sessionStore.active) {
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
                exerciseStore.customExercises.value,
                sessionStore.sessions.value.flatMap { it.sets } + sessionStore.active.value?.sets.orEmpty(),
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

    private fun savedUid() = openedUid?.let { id ->
        exerciseStore.customExercises.value.firstOrNull { it.uid == id }
    }?.uid

    private fun savedDraft() = exerciseStore.customExercises.value.firstOrNull { it.uid == uid }
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
        exerciseStore.saveExercise(uid, current.draft.copy(name = name, cues = cues))
        return true
    }

    /** Archives the exercise, or restores an archived one; nothing for one not saved yet. */
    fun toggleArchived() {
        val uid = uid ?: return
        exerciseStore.setArchived(uid, !state.value.archived)
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
