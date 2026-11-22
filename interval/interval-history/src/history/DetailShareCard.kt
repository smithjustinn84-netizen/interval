package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import dev.justinsmith.core.resources.heart_rate_unit
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_kcal_unit
import dev.justinsmith.interval.history.resources.detail_peak_bpm
import dev.justinsmith.interval.history.resources.detail_total
import dev.justinsmith.interval.history.resources.detail_work
import dev.justinsmith.interval.history.resources.history_calories
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.ui.ShareCardConfig
import dev.justinsmith.interval.ui.ShareCardSheet
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.WorkoutShareCard
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import dev.justinsmith.core.resources.Res as CoreRes

/** Whether [FinishedWorkout] has enough heart-rate samples to draw the share card's HR telemetry panel. */
internal val FinishedWorkout.hasShareHeartChart: Boolean
    get() = heartRateSamples?.count { it in HeartRate.BPM_RANGE }?.let { it >= 2 } == true

/** Share's interactive sheet for a finished workout in the Log: its [DetailShareCard] as PNG or text. */
@Composable
internal fun DetailShareSheet(
    workout: FinishedWorkout,
    text: String,
    onShareText: (String) -> Unit,
    onSharePicture: suspend (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    ShareCardSheet(
        text = text,
        onShareText = onShareText,
        onSharePicture = onSharePicture,
        onDismiss = onDismiss,
        hasHeartRate = workout.hasShareHeartChart,
        hasWeekBadge = workout.week != null,
        hasMovement = workout.movement != null,
    ) { config ->
        DetailShareCard(workout, config)
    }
}

/**
 * A workout in the Log as a printed telemetry card: when it was, its name and shape, its week standing badge,
 * optional movement watermark silhouette, timeline waveform, heart-rate zone-banding chart, and tabular
 * Barlow Condensed stats (total, work, peak BPM, calories).
 */
@Composable
internal fun DetailShareCard(workout: FinishedWorkout, config: ShareCardConfig = ShareCardConfig()) {
    val peakBpm = workout.heartRateSamples?.filter { it in HeartRate.BPM_RANGE }?.maxOrNull()
        ?: workout.heartRate?.peakBpm?.takeIf { it in HeartRate.BPM_RANGE }
    val stats = listOfNotNull(
        ShareStat(workout.total, stringResource(Res.string.detail_total)),
        ShareStat(workout.work, stringResource(Res.string.detail_work)),
        peakBpm?.let {
            ShareStat(
                value = it.toString(),
                label = stringResource(Res.string.detail_peak_bpm),
                unit = stringResource(CoreRes.string.heart_rate_unit),
                accent = true,
            )
        },
        workout.calories?.let {
            ShareStat(
                value = it.toString(),
                label = stringResource(Res.string.history_calories),
                unit = stringResource(Res.string.detail_kcal_unit),
            )
        },
    )
    WorkoutShareCard(
        eyebrow = stringResource(Res.string.common_joined, dayHeading(workout.day), workout.time),
        title = workout.presetName ?: workoutDescription(workout.config),
        subtitle = workout.presetName?.let { workoutDescription(workout.config) },
        config = workout.config,
        stats = stats,
        week = workout.week,
        heartRateSamples = workout.heartRateSamples,
        maxHeartRate = workout.maxHeartRate,
        movement = workout.movement,
        cardConfig = config,
    )
}
