package dev.justinsmith.bells.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.bands
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.library.LibraryList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The program being made or changed, whether it is already saved (so it can be deleted), and what its blocks
 * can be made of.
 */
data class ProgramEditorUiState(
    val draft: ProgramDraft = ProgramDraft(),
    val saved: Boolean = false,
    val choices: BlockChoices = BlockChoices(standardBells, offeredBands(bands(emptyList())), LibraryList(emptyList())),
)

/** Makes a program, or changes the one the user made under [uid], until it is saved or deleted. */
class ProgramEditorViewModel(
    private val repository: BellsRepository,
    private val filters: ExerciseFilterStore,
    uid: String?,
) : ViewModel() {
    // A program deleted meanwhile (on another page of the back stack) is made afresh.
    private val existing =
        uid?.let { id -> repository.customPrograms.value.firstOrNull { it.key == ProgramKey.Custom(id) } }
    private val uid = (existing?.key as? ProgramKey.Custom)?.uid
    private val _state = MutableStateFlow(
        ProgramEditorUiState(existing?.let(ProgramDraft::of) ?: ProgramDraft(), saved = existing != null),
    )

    private val choices = combine(
        repository.bells,
        repository.bands,
        repository.customExercises,
        filters.filters,
        repository.bar,
    ) { owned, bands, made, narrowed, bar ->
        blockChoices(owned, bands, made, narrowed, repository.sessions.value.setsOldestFirst(), bar)
    }

    val state: StateFlow<ProgramEditorUiState> = combine(_state, choices) { editing, choices ->
        editing.copy(choices = choices)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)

    fun filter(transform: (ExerciseFilters) -> ExerciseFilters) {
        filters.update(transform)
    }

    fun change(transform: (ProgramDraft) -> ProgramDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /** Saves the draft under [name]; false, and nothing saved, while it can't be. */
    fun save(name: String): Boolean {
        val draft = _state.value.draft.copy(name = name)
        if (!draft.canSave) return false
        repository.saveProgram(uid, draft)
        return true
    }

    fun delete() {
        uid?.let(repository::deleteProgram)
    }
}
