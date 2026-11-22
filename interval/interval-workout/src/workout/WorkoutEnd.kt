package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_end_confirm
import dev.justinsmith.interval.workout.resources.workout_end_nothing
import dev.justinsmith.interval.workout.resources.workout_end_progress
import dev.justinsmith.interval.workout.resources.workout_end_progress_cap
import dev.justinsmith.interval.workout.resources.workout_end_progress_moves
import dev.justinsmith.interval.workout.resources.workout_end_title
import dev.justinsmith.interval.workout.resources.workout_keep_paused
import dev.justinsmith.interval.workout.resources.workout_resume
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "End workout?", saying what is kept: its way back is Resume when End paused the workout to ask, else Keep paused.
 */
@Composable
internal fun EndDialog(state: WorkoutUiState, pausedToAsk: Boolean, onEnd: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.workout_end_title),
        text = if (state.hasProgress) endProgressText(state) else stringResource(Res.string.workout_end_nothing),
        confirm = stringResource(Res.string.workout_end_confirm),
        dismiss = stringResource(if (pausedToAsk) Res.string.workout_resume else Res.string.workout_keep_paused),
        onConfirm = onEnd,
        onDismiss = onDismiss,
        destructive = true,
    )
}

/** How far the workout got, as the End dialog says it: its rounds, a time cap's rounds done, or a flow's moves. */
@Composable
private fun endProgressText(state: WorkoutUiState): String = when {
    state.flow != null ->
        pluralStringResource(Res.plurals.workout_end_progress_moves, state.flow.of, state.flow.done, state.flow.of)

    state.caps.isNotEmpty() -> pluralStringResource(
        Res.plurals.workout_end_progress_cap,
        state.capRounds,
        state.capRounds,
    )

    else -> pluralStringResource(Res.plurals.workout_end_progress, state.rounds, state.completedRounds, state.rounds)
}
