package dev.justinsmith.bells.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.exerciseHistory
import dev.justinsmith.bells.domain.exerciseTrend
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.library.resources.Res
import dev.justinsmith.bells.library.resources.library_group
import dev.justinsmith.bells.library.resources.library_new
import dev.justinsmith.bells.library.resources.library_recent
import dev.justinsmith.bells.library.resources.library_title
import dev.justinsmith.bells.library.resources.library_yours
import dev.justinsmith.bells.ui.equipmentName
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** The library's list, and the page of the exercise open over it, if any. */
data class ExerciseLibraryUiState(
    val list: LibraryList = LibraryList(offeredExercises(emptyList())),
    val detail: ExerciseDetailUiState? = null,
)

/**
 * Every exercise on offer, the ones done lately first, narrowed by the filters the Log sheet shares; and the one
 * open, if any, with the user's history with it. Which one is open is kept in [savedState], so it survives the
 * process being let go.
 */
class ExerciseLibraryViewModel(
    repository: BellsRepository,
    private val filters: ExerciseFilterStore,
    private val timestamps: TimestampFormatter,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val opened = savedState.getStateFlow<String?>(KEY_OPENED, null)

    private val list = combine(
        repository.customExercises,
        repository.sessions,
        repository.active,
        filters.filters,
        combine(repository.bells, repository.bands, repository.bar, ::ownedEquipment),
    ) { made, sessions, active, narrowed, owned ->
        val offered = offeredExercises(made, archived = narrowed.showArchived)
        val history = sessions.setsOldestFirst() + active?.sets.orEmpty()
        LibraryList(offered, recentExercises(history, offered), narrowed, owned)
    }

    // The exercise open, by its key; null while none is, or once it is gone.
    private val detail = combine(opened, repository.sessions, repository.customExercises) { key, sessions, made ->
        key?.let { Exercise.parse(it, made) }?.let { exerciseDetail(it, sessions, timestamps) }
    }

    val state: StateFlow<ExerciseLibraryUiState> = combine(list, detail, ::ExerciseLibraryUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExerciseLibraryUiState())

    fun updateFilters(transform: (ExerciseFilters) -> ExerciseFilters) {
        filters.update(transform)
    }

    /** Opens the exercise [key] names, or closes the one open with null. */
    fun open(key: String?) {
        savedState[KEY_OPENED] = key
    }

    private companion object {
        const val KEY_OPENED = "opened"
    }
}

/** [exercise]'s page: the user's history with it in [sessions], each day as [timestamps] words it. */
fun exerciseDetail(
    exercise: Exercise,
    sessions: List<LiftSession>,
    timestamps: TimestampFormatter,
): ExerciseDetailUiState {
    val history = exerciseHistory(exercise, sessions)
    return ExerciseDetailUiState(
        exercise,
        history,
        lastDone = history.lastDoneAtEpochMillis?.let(timestamps::date).orEmpty(),
        recent = history.recent.map { DatedSession(it, timestamps.date(it.startedAtEpochMillis)) },
        trend = exerciseTrend(exercise, history.recent),
    )
}

/** The library, an exercise's page over it, and a new exercise's form; a session on its page by [onOpenSession]. */
@Composable
fun ExerciseLibraryRoute(onBack: () -> Unit, onOpenSession: (uid: String) -> Unit = {}) {
    val scope = currentKoinScope()
    val viewModel = viewModel {
        ExerciseLibraryViewModel(scope.get(), scope.get(), scope.get(), createSavedStateHandle())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    ExerciseLibraryScreen(
        state.list,
        onFilters = viewModel::updateFilters,
        onBack = onBack,
        onNew = { adding = it },
        onOpen = viewModel::open,
    )
    state.detail?.let { shown -> ExerciseDetailSheet(shown, onOpenSession, onDismiss = { viewModel.open(null) }) }
    adding?.let { name -> ExerciseEditorSheet(name, onDismiss = { adding = null }) }
}

/**
 * The exercise library, opened from Lift: every exercise there is to log, to look through and narrow. Under
 * the search and the filters, a card for the recent ones while nothing narrows, one for the user's own, then
 * one for each kind of equipment with any to show: one column on a phone, side by side as the window widens.
 * New adds one of the user's own, as does a search that finds nothing ([onNew], with the search). Any exercise
 * opens its page ([onOpen], by its key).
 */
@Composable
fun ExerciseLibraryScreen(
    list: LibraryList,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNew: (name: String) -> Unit = {},
    onOpen: (key: String) -> Unit = {},
) {
    val picker = rememberPickerState()
    val matches = pickerMatches(list, picker)
    val onPick = { exercise: Exercise -> onOpen(exercise.key) }
    val canPick = { _: Exercise -> true }
    val card = Modifier.padding(horizontal = Spacing.space16)
    TabGrid(
        stringResource(Res.string.library_title),
        modifier,
        navigation = { BackAction(onBack) },
        actions = { IconAction(IntervalIcons.Add, stringResource(Res.string.library_new), { onNew("") }) },
    ) {
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            PickerHeader(list, matches, picker, onFilters, card, archivable = true)
        }
        if (!matches.narrowed && list.recents.isNotEmpty()) {
            item(key = "recent") {
                Column(card) {
                    GroupLabel(stringResource(Res.string.library_recent))
                    ExerciseList(list.recents, matches.names, null, onPick, canPick, opens = true)
                }
            }
        }
        val (yours, builtIn) = matches.shown.partition { it is CustomExercise }
        if (yours.isNotEmpty()) {
            item(key = "yours") {
                Column(card) {
                    GroupLabel(
                        stringResource(Res.string.library_group, stringResource(Res.string.library_yours), yours.size),
                    )
                    ExerciseList(yours, matches.names, null, onPick, canPick, opens = true, tagYours = false)
                }
            }
        }
        val byEquipment = builtIn.groupBy { it.equipment }
        Equipment.entries.forEach { equipment ->
            val exercises = byEquipment[equipment] ?: return@forEach
            item(key = "group-${equipment.name}") {
                Column(card) {
                    GroupLabel(stringResource(Res.string.library_group, equipmentName(equipment), exercises.size))
                    ExerciseList(exercises, matches.names, null, onPick, canPick, opens = true, equipment = false)
                }
            }
        }
        if (matches.shown.isEmpty()) {
            item(key = "none", span = StaggeredGridItemSpan.FullLine) {
                NoMatch(picker.query.text.toString(), onNew, card)
            }
        }
    }
}
