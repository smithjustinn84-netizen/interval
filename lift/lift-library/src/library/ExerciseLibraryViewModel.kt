package dev.justinsmith.lift.library

import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.ExerciseStore
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.bestPaceOf
import dev.justinsmith.lift.domain.exerciseHistory
import dev.justinsmith.lift.domain.exerciseTrend
import dev.justinsmith.lift.domain.offeredExercises
import dev.justinsmith.lift.domain.ownedEquipment
import dev.justinsmith.lift.domain.recentExercises
import dev.justinsmith.lift.domain.setsOldestFirst
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    sessionStore: SessionStore,
    exerciseStore: ExerciseStore,
    gearStore: GearStore,
    private val filters: ExerciseFilterStore,
    private val timestamps: TimestampFormatter,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val opened = savedState.getStateFlow<String?>(KEY_OPENED, null)

    private val list = combine(
        exerciseStore.customExercises,
        sessionStore.sessions,
        sessionStore.active,
        filters.filters,
        combine(gearStore.bells, gearStore.bands, gearStore.bar, ::ownedEquipment),
    ) { made, sessions, active, narrowed, owned ->
        val offered = offeredExercises(made, archived = narrowed.showArchived)
        val history = sessions.setsOldestFirst() + active?.sets.orEmpty()
        LibraryList(offered, recentExercises(history, offered), narrowed, owned)
    }

    // The exercise open, by its key; null while none is, or once it is gone.
    private val detail = combine(opened, sessionStore.sessions, exerciseStore.customExercises) { key, sessions, made ->
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
        bestPace = bestPaceOf(exercise, sessions),
    )
}
