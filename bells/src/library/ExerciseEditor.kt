package dev.justinsmith.bells.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import dev.justinsmith.bells.resources.exercise_save
import dev.justinsmith.bells.resources.library_focus
import dev.justinsmith.bells.resources.library_measured
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.equipmentName
import dev.justinsmith.bells.ui.focusName
import dev.justinsmith.bells.ui.measureName
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * The exercise being added or changed: whether it is saved already, the names of the user's others it can't
 * take, and whether sets of it are logged, which fixes its equipment and measure.
 */
data class ExerciseEditorUiState(
    val draft: ExerciseDraft = ExerciseDraft(),
    val saved: Boolean = false,
    val taken: Set<String> = emptySet(),
    val locked: Boolean = false,
)

/**
 * Adds an exercise, named [name] to start (from a search that found nothing), or changes the user's [uid];
 * one archived or gone meanwhile is added afresh.
 */
class ExerciseEditorViewModel(private val repository: BellsRepository, uid: String?, name: String) : ViewModel() {
    private val existing = uid?.let { id -> repository.customExercises.value.firstOrNull { it.uid == id } }
    private val uid = existing?.uid
    private val _state = MutableStateFlow(
        ExerciseEditorUiState(
            draft = existing?.let {
                ExerciseDraft(it.name, it.equipment, it.measure, it.perSide, it.focus, it.cues)
            } ?: ExerciseDraft(name = name.trim()),
            saved = existing != null,
            taken = takenNames(repository.customExercises.value, uid),
            locked = existing != null &&
                (repository.sessions.value.flatMap { it.sets } + repository.active.value?.sets.orEmpty())
                    .any { it.exercise.key == existing.key },
        ),
    )
    val state: StateFlow<ExerciseEditorUiState> = _state.asStateFlow()

    /** Changes the draft; a locked one keeps its equipment and measure. */
    fun change(transform: (ExerciseDraft) -> ExerciseDraft) {
        _state.update { state ->
            val changed = transform(state.draft)
            state.copy(
                draft = if (state.locked) {
                    changed.copy(equipment = state.draft.equipment, measure = state.draft.measure)
                } else {
                    changed
                },
            )
        }
    }

    /** Saves it as [name] with [cues]; false, and nothing saved, while it can't be. */
    fun save(name: String, cues: String): Boolean {
        val state = _state.value
        if (!state.draft.canSave(name, state.taken)) return false
        repository.saveExercise(uid, state.draft.copy(name = name, cues = cues))
        return true
    }
}

@Composable
fun ExerciseEditorRoute(uid: String?, name: String, onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ExerciseEditorViewModel(scope.get(), uid, name) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseEditorScreen(
        state,
        onChange = viewModel::change,
        onSave = { name, cues -> if (viewModel.save(name, cues)) onBack() },
        onBack = onBack,
    )
}

/** An exercise of the user's: its name, what it is done with, how it is counted and sided, its focus, cues. */
@Composable
fun ExerciseEditorScreen(
    state: ExerciseEditorUiState,
    onChange: ((ExerciseDraft) -> ExerciseDraft) -> Unit,
    onSave: (name: String, cues: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val palette = IntervalTheme.palette
    val name = rememberTextFieldState(draft.name)
    val cues = rememberTextFieldState(draft.cues)
    val title = stringResource(if (state.saved) Res.string.exercise_editor_edit else Res.string.exercise_editor_new)
    ScrollPage(title, modifier, navigation = { BackAction(onBack) }) {
        val typed = name.text.toString()
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
        // Save is offered once there is a name of the user's own to save it as.
        if (draft.canSave(typed, state.taken)) {
            AccentButton(
                stringResource(Res.string.exercise_save),
                { onSave(typed.trim(), cues.text.toString()) },
                Modifier.fillMaxWidth().padding(top = Spacing.space16),
            )
        }
    }
}
