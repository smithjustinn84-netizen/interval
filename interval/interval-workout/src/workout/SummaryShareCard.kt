package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.WorkoutShareCard
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_done
import dev.justinsmith.interval.workout.resources.workout_ended
import dev.justinsmith.interval.workout.resources.workout_stat_calories
import dev.justinsmith.interval.workout.resources.workout_stat_moves
import dev.justinsmith.interval.workout.resources.workout_stat_rounds
import dev.justinsmith.interval.workout.resources.workout_stat_rounds_value
import dev.justinsmith.interval.workout.resources.workout_stat_time
import org.jetbrains.compose.resources.stringResource

/**
 * The finished workout as a picture: Done (or Ended), its name and shape, its timeline and the heart rate a
 * sensor read, then the summary's own figures, its time, rounds (or moves, or a time cap's rounds) and calories.
 */
@Composable
internal fun SummaryShareCard(state: WorkoutUiState) {
    val counted = if (state.caps.isNotEmpty()) {
        state.capRounds.toString()
    } else {
        stringResource(Res.string.workout_stat_rounds_value, state.countDone, state.countOf)
    }
    val stats = listOfNotNull(
        ShareStat(formatClock(state.elapsedSeconds), stringResource(Res.string.workout_stat_time)),
        ShareStat(
            counted,
            stringResource(if (state.flow != null) Res.string.workout_stat_moves else Res.string.workout_stat_rounds),
        ),
        state.calories?.let { ShareStat(it.toString(), stringResource(Res.string.workout_stat_calories)) },
    )
    WorkoutShareCard(
        eyebrow = stringResource(if (state.ended) Res.string.workout_ended else Res.string.workout_done),
        title = presetNameOrCustom(state.presetName),
        subtitle = workoutDescription(state.config),
        config = state.config,
        stats = stats,
        heartRateSamples = state.heartRateSamples,
        maxHeartRate = state.maxHeartRate,
    )
}
