package dev.justinsmith.lift.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RestClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The program followed, if any, and its next day; the programs the user made; and the rest between sets, which
 * each day's length is estimated with.
 */
data class ProgramsUiState(
    val following: Program? = null,
    val followingDay: ProgramDay? = null,
    val custom: List<Program> = emptyList(),
    val restSeconds: Int = RestClock.DEFAULT_SECONDS,
)

/** The built-in programs, the ones the user made, which one is followed and its next day. */
class ProgramsViewModel(private val programStore: ProgramStore, setStore: SetStore) : ViewModel() {
    val state: StateFlow<ProgramsUiState> =
        combine(
            programStore.following,
            programStore.programDay,
            programStore.customPrograms,
            setStore.restSeconds,
            ::ProgramsUiState,
        )
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ProgramsUiState(
                    programStore.following.value,
                    programStore.programDay.value,
                    programStore.customPrograms.value,
                    setStore.restSeconds.value,
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
