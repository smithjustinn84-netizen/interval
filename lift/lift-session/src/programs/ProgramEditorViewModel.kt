package dev.justinsmith.lift.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.ExerciseStore
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.decodeDraftDays
import dev.justinsmith.lift.data.encodeDays
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.copyName
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.domain.setsOldestFirst
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.library.LibraryList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet

/**
 * The program being made or changed, what it started as (so unsaved edits can ask before leaving), whether it is
 * already saved (so it can be deleted), and what its blocks can be made of.
 */
data class ProgramEditorUiState(
    val draft: ProgramDraft = ProgramDraft(),
    val initialDraft: ProgramDraft = ProgramDraft(),
    val saved: Boolean = false,
    /** For a copy of the built-in followed, the day the copy carries on from; else null. */
    val carriesOnFrom: Int? = null,
    val choices: BlockChoices = BlockChoices(standardBells, offeredBands(bands(emptyList())), LibraryList(emptyList())),
)

/**
 * A built-in to make a copy of, [of], named from [base] ("Easy Strength (yours)") as the screen words it: a number
 * is added while a program of the user's has the name.
 */
data class ProgramCopy(val of: ProgramId, val base: String)

/**
 * Makes a program, or changes the one the user made under [uid], until it is saved or deleted; with [copy], makes
 * one from a built-in.
 */
class ProgramEditorViewModel(
    private val programStore: ProgramStore,
    private val exerciseStore: ExerciseStore,
    private val gearStore: GearStore,
    private val sessionStore: SessionStore,
    private val filters: ExerciseFilterStore,
    uid: String?,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    copy: ProgramCopy? = null,
) : ViewModel() {
    // A program deleted meanwhile (on another page of the back stack) is made afresh.
    private val existing =
        uid?.let { id -> programStore.customPrograms.value.firstOrNull { it.key == ProgramKey.Custom(id) } }
    private val uid = (existing?.key as? ProgramKey.Custom)?.uid
    private val copyOf = copy?.takeIf { existing == null }?.let { ProgramKey.BuiltIn(it.of) }
    private val initialDraft = when {
        existing != null -> ProgramDraft.of(existing)

        copy != null -> ProgramDraft.of(program(copy.of))
            .copy(name = copyName(copy.base, programStore.customPrograms.value.mapNotNull { it.name }.toSet()))

        else -> ProgramDraft()
    }
    private val _state = MutableStateFlow(
        ProgramEditorUiState(
            draft = restoreDraft(savedState, exerciseStore.customExercises.value, initialDraft),
            initialDraft = initialDraft,
            saved = existing != null,
            carriesOnFrom = programStore.programDay.value?.takeIf { it.program.key == copyOf }?.number,
        ),
    )

    // The history follows the sessions, so one finished while the editor is open is among the recent.
    private val choices = combine(
        combine(gearStore.bells, gearStore.bands, gearStore.bar, ::Triple),
        exerciseStore.customExercises,
        filters.filters,
        sessionStore.sessions.map { it.setsOldestFirst() },
    ) { (owned, bands, bar), made, narrowed, history ->
        blockChoices(owned, bands, made, narrowed, history, bar)
    }

    val state: StateFlow<ProgramEditorUiState> = combine(_state, choices) { editing, choices ->
        editing.copy(choices = choices)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)

    fun filter(transform: (ExerciseFilters) -> ExerciseFilters) {
        filters.update(transform)
    }

    fun change(transform: (ProgramDraft) -> ProgramDraft) {
        // Kept once the change is in, outside the update, whose lambda can run more than once.
        val next = _state.updateAndGet { it.copy(draft = transform(it.draft)) }.draft
        persistDraft(savedState, next)
    }

    /** Saves the draft under [name]; false, and nothing saved, while it can't be. */
    fun save(name: String): Boolean {
        val draft = _state.value.draft.copy(name = name)
        if (!draft.canSave) return false
        copyOf?.let { programStore.saveCopy(it, draft) } ?: programStore.saveProgram(uid, draft)
        return true
    }

    fun delete() {
        uid?.let(programStore::deleteProgram)
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
