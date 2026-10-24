package dev.justinsmith.bells.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.library_new
import dev.justinsmith.bells.resources.library_title
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.ScrollPage
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
fun ExerciseLibraryRoute(onBack: () -> Unit, onNew: (name: String) -> Unit = {}, onEdit: (uid: String) -> Unit = {}) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ExerciseLibraryViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseLibraryScreen(state, onFilters = viewModel::updateFilters, onBack = onBack, onNew = onNew, onEdit = onEdit)
}

/**
 * The exercise library, opened from Lift: every exercise there is to log, to look through and narrow. New
 * adds one of the user's own, as does a search that finds nothing ([onNew], with the search); one of theirs
 * opens to change ([onEdit]).
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
    ScrollPage(
        stringResource(Res.string.library_title),
        modifier,
        navigation = { BackAction(onBack) },
        actions = { IconAction(IntervalIcons.Add, stringResource(Res.string.library_new), { onNew("") }) },
    ) {
        ExercisePicker(
            list,
            onFilters,
            onPick = { exercise -> (exercise as? CustomExercise)?.let { onEdit(it.uid) } },
            canPick = { it is CustomExercise },
            onAdd = onNew,
            archivable = true,
        )
    }
}
