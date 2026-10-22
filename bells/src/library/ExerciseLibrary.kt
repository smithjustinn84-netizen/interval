package dev.justinsmith.bells.library

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.library_title
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Every exercise on offer, the ones done lately first, narrowed by the filters the Log sheet shares. */
class ExerciseLibraryViewModel(repository: BellsRepository, private val filters: ExerciseFilterStore) : ViewModel() {
    val state: StateFlow<LibraryList> =
        combine(repository.customExercises, repository.sessions, repository.active, filters.filters) {
                made,
                sessions,
                active,
                narrowed,
            ->
            val offered = offeredExercises(made)
            val history = sessions.reversed().flatMap { it.sets } + active?.sets.orEmpty()
            LibraryList(offered, recentExercises(history, offered), narrowed)
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
    ExerciseLibraryScreen(state, onFilters = viewModel::updateFilters, onBack = onBack)
}

/** The exercise library, opened from Lift: every exercise there is to log, to look through and narrow. */
@Composable
fun ExerciseLibraryScreen(
    list: LibraryList,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollPage(stringResource(Res.string.library_title), modifier, navigation = { BackAction(onBack) }) {
        ExercisePicker(
            list,
            onFilters,
            Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24),
        )
    }
}
