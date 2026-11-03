package dev.justinsmith.lift.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The program followed, if any, and its next day; and the programs the user made. */
data class ProgramsUiState(
    val following: Program? = null,
    val followingDay: ProgramDay? = null,
    val custom: List<Program> = emptyList(),
)

/** The built-in programs, the ones the user made, which one is followed and its next day. */
class ProgramsViewModel(private val programStore: ProgramStore) : ViewModel() {
    val state: StateFlow<ProgramsUiState> =
        combine(programStore.following, programStore.programDay, programStore.customPrograms, ::ProgramsUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ProgramsUiState(
                    programStore.following.value,
                    programStore.programDay.value,
                    programStore.customPrograms.value,
                ),
            )

    /** Follows [key] from its first day; the program followed already keeps its place. */
    fun follow(key: ProgramKey) {
        if (key != programStore.following.value?.key) programStore.follow(key)
    }

    fun stop() {
        programStore.stopFollowing()
    }
}
