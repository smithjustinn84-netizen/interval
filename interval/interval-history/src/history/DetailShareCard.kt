package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_total
import dev.justinsmith.interval.history.resources.detail_work
import dev.justinsmith.interval.history.resources.history_calories
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.WorkoutShareCard
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * A workout in the Log as a picture: when it was, its name and shape, its timeline and the heart rate a sensor
 * read, then its total, its work and its calories, as its page has them.
 */
@Composable
internal fun DetailShareCard(workout: FinishedWorkout) {
    val stats = listOfNotNull(
        ShareStat(workout.total, stringResource(Res.string.detail_total)),
        ShareStat(workout.work, stringResource(Res.string.detail_work)),
        workout.calories?.let { ShareStat(it.toString(), stringResource(Res.string.history_calories)) },
    )
    WorkoutShareCard(
        eyebrow = stringResource(Res.string.common_joined, dayHeading(workout.day), workout.time),
        title = workout.presetName ?: workoutDescription(workout.config),
        subtitle = workout.presetName?.let { workoutDescription(workout.config) },
        config = workout.config,
        stats = stats,
        heartRateSamples = workout.heartRateSamples,
        maxHeartRate = workout.maxHeartRate,
    )
}
