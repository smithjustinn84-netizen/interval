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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.library_group
import dev.justinsmith.bells.resources.library_new
import dev.justinsmith.bells.resources.library_recent
import dev.justinsmith.bells.resources.library_title
import dev.justinsmith.bells.resources.library_yours
import dev.justinsmith.bells.ui.equipmentName
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

/** Every exercise on offer, the ones done lately first, narrowed by the filters the Log sheet shares. */
class ExerciseLibraryViewModel(repository: BellsRepository, private val filters: ExerciseFilterStore) : ViewModel() {
    val state: StateFlow<LibraryList> =
        combine(
            repository.customExercises,
            repository.sessions,
            repository.active,
            filters.filters,
            combine(repository.bells, repository.bands, repository.bar, ::ownedEquipment),
        ) { made, sessions, active, narrowed, owned ->
            val offered = offeredExercises(made, archived = narrowed.showArchived)
            val history = sessions.setsOldestFirst() + active?.sets.orEmpty()
            LibraryList(offered, recentExercises(history, offered), narrowed, owned)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryList(offeredExercises(emptyList())))

    fun updateFilters(transform: (ExerciseFilters) -> ExerciseFilters) {
        filters.update(transform)
    }
}

@Composable
fun ExerciseLibraryRoute(onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ExerciseLibraryViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    ExerciseLibraryScreen(
        state,
        onFilters = viewModel::updateFilters,
        onBack = onBack,
        onNew = { adding = it },
        onEdit = { editing = it },
    )
    if (editing != null || adding != null) {
        ExerciseEditorSheet(editing, adding.orEmpty(), onDismiss = {
            editing = null
            adding = null
        })
    }
}

/**
 * The exercise library, opened from Lift: every exercise there is to log, to look through and narrow. Under
 * the search and the filters, a card for the recent ones while nothing narrows, one for the user's own, then
 * one for each kind of equipment with any to show: one column on a phone, side by side as the window widens.
 * New adds one of the user's own, as does a search that finds nothing ([onNew], with the search); one of theirs
 * opens to change ([onEdit]). The built-ins just inform, so take no tap.
 */
@Composable
fun ExerciseLibraryScreen(
    list: LibraryList,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNew: (name: String) -> Unit = {},
    onEdit: (uid: String) -> Unit = {},
) {
    val picker = rememberPickerState()
    val matches = pickerMatches(list, picker)
    val onPick = { exercise: Exercise -> if (exercise is CustomExercise) onEdit(exercise.uid) }
    val canPick = { exercise: Exercise -> exercise is CustomExercise }
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
