package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.common_joined
import dev.justinsmith.interval.workout.resources.share_workout
import dev.justinsmith.interval.workout.resources.share_workout_calories
import dev.justinsmith.interval.workout.resources.share_workout_figures
import dev.justinsmith.interval.workout.resources.workout_stat_rounds_value
import org.jetbrains.compose.resources.stringResource

/**
 * The finished workout in words to send: its name and shape, then its rounds, time and calories:
 * "Tabata · 20s on · 10s off · ×8" over "8/8 rounds · 4:00 · 62 kcal".
 */
@Composable
internal fun workoutShareText(state: WorkoutUiState): String {
    val shape = stringResource(
        Res.string.common_joined,
        presetNameOrCustom(state.presetName),
        workoutDescription(state.config),
    )
    val rounds = stringResource(Res.string.workout_stat_rounds_value, state.completedRounds, state.rounds)
    val figures = stringResource(Res.string.share_workout_figures, rounds, formatClock(state.elapsedSeconds))
    val line = state.calories?.let { stringResource(Res.string.share_workout_calories, figures, it) } ?: figures
    return stringResource(Res.string.share_workout, shape, line)
}
