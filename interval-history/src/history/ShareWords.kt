package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.share_detail
import dev.justinsmith.interval.history.resources.share_detail_calories
import dev.justinsmith.interval.history.resources.share_detail_figures
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * A finished workout in words to send: its name, when it was, then its total, work time and calories:
 * "HIIT 30/30 · Wednesday, Oct 14 · 7:05 AM" over "9:40 total · 5:00 work · 84 kcal".
 */
@Composable
internal fun workoutShareText(workout: FinishedWorkout): String {
    val title = workout.presetName ?: workoutDescription(workout.config)
    val moment = stringResource(Res.string.common_joined, dayHeading(workout.day), workout.time)
    val figures = stringResource(Res.string.share_detail_figures, workout.total, workout.work)
    val line = workout.calories?.let { stringResource(Res.string.share_detail_calories, figures, it) } ?: figures
    return stringResource(Res.string.share_detail, stringResource(Res.string.common_joined, title, moment), line)
}
