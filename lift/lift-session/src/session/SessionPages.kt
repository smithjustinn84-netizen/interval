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

        // Without the ring's share, which moves every tick: the page changes only as what it says does, and the ring
        // reads it from the clocks where it is drawn.
        emom != null && emomState != null -> SessionPage.Emom(emom, emomState.copy(minuteProgress = 0f))

        density != null && densityState != null -> SessionPage.Density(density, densityState.copy(progress = 0f))

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

/**
 * A ring's share as [read] gives it, held at the last one once it gives none: a page fading out after its clock
 * stopped keeps its ring where it was.
 */
internal fun heldShare(read: () -> Float?): () -> Float {
    var last = 0f
    return { read()?.also { last = it } ?: last }
}
