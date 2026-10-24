package dev.justinsmith.bells.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.MAX_EXERCISE_CUES
import dev.justinsmith.bells.domain.MAX_EXERCISE_NAME
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.canSave
import dev.justinsmith.bells.domain.exerciseNameKey
import dev.justinsmith.bells.domain.takenNames
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.exercise_archive
import dev.justinsmith.bells.resources.exercise_archive_hint
import dev.justinsmith.bells.resources.exercise_archived_note
import dev.justinsmith.bells.resources.exercise_cues
import dev.justinsmith.bells.resources.exercise_cues_placeholder
import dev.justinsmith.bells.resources.exercise_editor_edit
import dev.justinsmith.bells.resources.exercise_editor_new
import dev.justinsmith.bells.resources.exercise_equipment
import dev.justinsmith.bells.resources.exercise_locked
import dev.justinsmith.bells.resources.exercise_name
import dev.justinsmith.bells.resources.exercise_name_placeholder
import dev.justinsmith.bells.resources.exercise_name_taken
import dev.justinsmith.bells.resources.exercise_one_side
import dev.justinsmith.bells.resources.exercise_one_side_hint
import dev.justinsmith.bells.resources.exercise_restore
import dev.justinsmith.bells.resources.exercise_save
import dev.justinsmith.bells.resources.library_focus
import dev.justinsmith.bells.resources.library_measured
import dev.justinsmith.bells.resources.notice_exercise_saved
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.equipmentName
import dev.justinsmith.bells.ui.focusName
import dev.justinsmith.bells.ui.measureName
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
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
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
 * The exercise a sheet is adding, named [name] to start (from a search that found nothing), or changing,
 * archiving or restoring: the user's [uid]; one gone meanwhile is added afresh. It lives as long as the sheet,
 * kept by [rememberExerciseEditor] across a configuration change and process death, so a sheet opened again
 * starts from what is saved rather than from a draft left behind.
 */
@Stable
class ExerciseEditor(
    private val repository: BellsRepository,
    private val openedUid: String?,
    private val openedName: String,
    restored: ExerciseDraft? = null,
) {
    private val existing = openedUid?.let { id -> repository.customExercises.value.firstOrNull { it.uid == id } }
    private val uid = existing?.uid

    var state by mutableStateOf(
        ExerciseEditorUiState(
            draft = restored ?: existing?.let {
                ExerciseDraft(it.name, it.equipment, it.measure, it.perSide, it.focus, it.cues)
            } ?: ExerciseDraft(name = openedName.trim()),
            saved = existing != null,
            archived = existing?.archived == true,
            taken = takenNames(repository.customExercises.value, uid),
            locked = existing != null &&
                (repository.sessions.value.flatMap { it.sets } + repository.active.value?.sets.orEmpty())
                    .any { it.exercise.key == existing.key },
        ),
    )
        private set

    /** Changes the draft; a locked one keeps its equipment and measure. */
    fun change(transform: (ExerciseDraft) -> ExerciseDraft) {
        val changed = transform(state.draft)
        state = state.copy(
            draft = if (state.locked) {
                changed.copy(equipment = state.draft.equipment, measure = state.draft.measure)
            } else {
                changed
            },
        )
    }

    /** Saves it as [name] with [cues]; false, and nothing saved, while it can't be. */
    fun save(name: String, cues: String): Boolean {
        if (!state.draft.canSave(name, state.taken)) return false
        repository.saveExercise(uid, state.draft.copy(name = name, cues = cues))
        return true
    }

    /** Archives the exercise, or restores an archived one; nothing for one not saved yet. */
    fun toggleArchived() {
        val uid = uid ?: return
        repository.setArchived(uid, !state.archived)
    }

    companion object {
        /** Keeps what was opened and the draft, enums by their names: not every platform saves an enum. */
        fun saver(repository: BellsRepository): Saver<ExerciseEditor, Any> = mapSaver(
            save = {
                val draft = it.state.draft
                mapOf(
                    UID to it.openedUid.orEmpty(),
                    OPENED_NAME to it.openedName,
                    NAME to draft.name,
                    EQUIPMENT to draft.equipment.name,
                    MEASURE to draft.measure.name,
                    PER_SIDE to draft.perSide,
                    FOCUS to draft.focus.name,
                    CUES to draft.cues,
                )
            },
            restore = {
                ExerciseEditor(
                    repository,
                    openedUid = (it.getValue(UID) as String).ifEmpty { null },
                    openedName = it.getValue(OPENED_NAME) as String,
                    restored = ExerciseDraft(
                        name = it.getValue(NAME) as String,
                        equipment = Equipment.valueOf(it.getValue(EQUIPMENT) as String),
                        measure = Measure.valueOf(it.getValue(MEASURE) as String),
                        perSide = it.getValue(PER_SIDE) as Boolean,
                        focus = Focus.valueOf(it.getValue(FOCUS) as String),
                        cues = it.getValue(CUES) as String,
                    ),
                )
            },
        )

        private const val UID = "uid"
        private const val OPENED_NAME = "openedName"
        private const val NAME = "name"
        private const val EQUIPMENT = "equipment"
        private const val MEASURE = "measure"
        private const val PER_SIDE = "perSide"
        private const val FOCUS = "focus"
        private const val CUES = "cues"
    }
}

/** The [ExerciseEditor] for [uid], or a new exercise named [name], for as long as the caller shows it. */
@Composable
fun rememberExerciseEditor(uid: String?, name: String): ExerciseEditor {
    val repository = currentKoinScope().get<BellsRepository>()
    val saver = remember(repository) { ExerciseEditor.saver(repository) }
    return rememberSaveable(uid, name, saver = saver) { ExerciseEditor(repository, uid, name) }
}

/**
 * The sheet that adds an exercise of the user's, named [name] to start, or changes their [uid]: beside the
 * page on a tablet, rising from the bottom on a phone, as a band or a preset does. Saving, archiving and
 * restoring close it; so does every other way out, which saves nothing.
 */
@Composable
fun ExerciseEditorSheet(uid: String?, name: String, onDismiss: () -> Unit) {
    val editor = rememberExerciseEditor(uid, name)
    val title = stringResource(
        if (editor.state.saved) Res.string.exercise_editor_edit else Res.string.exercise_editor_new,
    )
    IntervalSheet(title, onDismiss = onDismiss) { hide ->
        ExerciseEditorForm(
            editor.state,
            onChange = editor::change,
            onSave = { typed, cues -> if (editor.save(typed, cues)) hide() },
            onArchive = {
                editor.toggleArchived()
                hide()
            },
            modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        val typed = name.text.toString()
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
            onDone = {},
            label = stringResource(Res.string.exercise_name),
            maxLength = MAX_EXERCISE_NAME,
        )
        if (exerciseNameKey(typed) in state.taken) {
            Text(stringResource(Res.string.exercise_name_taken), style = IntervalTheme.type.meta, color = palette.text)
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
            onDone = {},
            label = stringResource(Res.string.exercise_cues),
            maxLength = MAX_EXERCISE_CUES,
        )
        SaveAction(draft.canSave(typed, state.taken), typed.trim(), onSave = { onSave(it, cues.text.toString()) })
        if (state.saved) {
            SecondaryButton(
                stringResource(if (state.archived) Res.string.exercise_restore else Res.string.exercise_archive),
                onArchive,
                Modifier.fillMaxWidth().padding(top = Spacing.space8),
            )
            if (!state.archived) {
                Text(
                    stringResource(Res.string.exercise_archive_hint),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
        }
    }
}

/**
 * Save, offered once there is a name of the user's own to save under, opening the room it takes; it
 * says what it saved as it goes.
 */
@Composable
private fun SaveAction(canSave: Boolean, name: String, onSave: (name: String) -> Unit) {
    val notices = LocalNotices.current
    val saved = stringResource(Res.string.notice_exercise_saved, name)
    AnimatedVisibility(canSave, enter = enterExpanding(), exit = exitShrinking()) {
        AccentButton(
            stringResource(Res.string.exercise_save),
            {
                notices.show(saved)
                onSave(name)
            },
            Modifier.fillMaxWidth().padding(top = Spacing.space16),
        )
    }
}
