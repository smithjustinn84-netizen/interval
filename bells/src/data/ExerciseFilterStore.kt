package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.ExerciseFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The library's filters, shared by its page and the Log sheet's picker, so narrowing one narrows both. Held
 * in memory for as long as the app's container lives, never saved.
 */
class ExerciseFilterStore {
    private val _filters = MutableStateFlow(ExerciseFilters())
    val filters: StateFlow<ExerciseFilters> = _filters.asStateFlow()

    fun update(transform: (ExerciseFilters) -> ExerciseFilters) {
        _filters.update(transform)
    }
}
