package dev.justinsmith.bells.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.data.decodeDraftDays
import dev.justinsmith.bells.data.encodeDays
import dev.justinsmith.bells.domain.CustomExercise
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
 * The program being made or changed, what it started as (so unsaved edits can ask before leaving), whether it is
 * already saved (so it can be deleted), and what its blocks can be made of.
 */
data class ProgramEditorUiState(
    val draft: ProgramDraft = ProgramDraft(),
    val initialDraft: ProgramDraft = ProgramDraft(),
    val saved: Boolean = false,
    val choices: BlockChoices = BlockChoices(standardBells, offeredBands(bands(emptyList())), LibraryList(emptyList())),
)

/** Makes a program, or changes the one the user made under [uid], until it is saved or deleted. */
class ProgramEditorViewModel(
    private val repository: BellsRepository,
    private val filters: ExerciseFilterStore,
    uid: String?,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    // A program deleted meanwhile (on another page of the back stack) is made afresh.
    private val existing =
        uid?.let { id -> repository.customPrograms.value.firstOrNull { it.key == ProgramKey.Custom(id) } }
    private val uid = (existing?.key as? ProgramKey.Custom)?.uid
    private val initialDraft = existing?.let(ProgramDraft::of) ?: ProgramDraft()
    private val _state = MutableStateFlow(
        ProgramEditorUiState(
            draft = restoreDraft(savedState, repository.customExercises.value, initialDraft),
            initialDraft = initialDraft,
            saved = existing != null,
        ),
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
        _state.update { current ->
            val next = transform(current.draft)
            persistDraft(savedState, next)
            current.copy(draft = next)
        }
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

private const val KEY_DRAFT_NAME = "draft_name"
private const val KEY_DRAFT_DAYS_PER_WEEK = "draft_days_per_week"
private const val KEY_DRAFT_WEEKS = "draft_weeks"
private const val KEY_DRAFT_DAYS = "draft_days"

private fun restoreDraft(
    savedState: SavedStateHandle,
    made: List<CustomExercise>,
    fallback: ProgramDraft,
): ProgramDraft {
    val encodedDays = savedState.get<String>(KEY_DRAFT_DAYS)
    val days = encodedDays?.let { decodeDraftDays(it, made) } ?: fallback.days
    return ProgramDraft(
        name = savedState[KEY_DRAFT_NAME] ?: fallback.name,
        days = days,
        daysPerWeek = (savedState[KEY_DRAFT_DAYS_PER_WEEK] ?: fallback.daysPerWeek)
            .coerceIn(1, ProgramDraft.MAX_DAYS_PER_WEEK),
        weeks = (savedState[KEY_DRAFT_WEEKS] ?: fallback.weeks)
            .coerceIn(1, ProgramDraft.MAX_WEEKS),
    )
}

private fun persistDraft(savedState: SavedStateHandle, draft: ProgramDraft) {
    savedState[KEY_DRAFT_NAME] = draft.name
    savedState[KEY_DRAFT_DAYS_PER_WEEK] = draft.daysPerWeek
    savedState[KEY_DRAFT_WEEKS] = draft.weeks
    savedState[KEY_DRAFT_DAYS] = encodeDays(draft.days)
}
