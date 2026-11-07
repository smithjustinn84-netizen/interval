package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.DensityState
import dev.justinsmith.lift.domain.EmomState

/**
 * The face up: the summary once finished, else an EMOM or a density block running, else the session itself.
 */
internal fun sessionPage(state: SessionUiState, clocks: SessionClocks): SessionPage {
    val finished = state.finished
    val emom = state.emom
    val emomState = clocks.emomState
    val density = state.density
    val densityState = clocks.densityState
    return when {
        finished != null -> SessionPage.Summary(finished)
        emom != null && emomState != null -> SessionPage.Emom(emom, emomState)
        density != null && densityState != null -> SessionPage.Density(density, densityState)
        else -> SessionPage.Logging
    }
}

/** Which of the Lifting page's faces is up. Each carries what it shows, so a face on its way out still has it. */
internal sealed interface SessionPage {
    data class Summary(val finished: Finished) : SessionPage

    data class Emom(val run: EmomRun, val state: EmomState) : SessionPage

    data class Density(val run: DensityRun, val state: DensityState) : SessionPage

    data object Logging : SessionPage
}
