package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import dev.justinsmith.core.resources.heart_rate_unit
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.ShareCardConfig
import dev.justinsmith.interval.ui.ShareCardSheet
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.WorkoutShareCard
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.interval.ui.workoutShareMovement
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_done
import dev.justinsmith.interval.workout.resources.workout_ended
import dev.justinsmith.interval.workout.resources.workout_stat_calories
import dev.justinsmith.interval.workout.resources.workout_stat_kcal_unit
import dev.justinsmith.interval.workout.resources.workout_stat_moves
import dev.justinsmith.interval.workout.resources.workout_stat_peak_bpm
import dev.justinsmith.interval.workout.resources.workout_stat_rounds
import dev.justinsmith.interval.workout.resources.workout_stat_rounds_value
import dev.justinsmith.interval.workout.resources.workout_stat_time
import dev.justinsmith.interval.workout.resources.workout_stat_work
import org.jetbrains.compose.resources.stringResource
import dev.justinsmith.core.resources.Res as CoreRes

/** Primary movement silhouette for [state]'s share card, if one applies. */
internal fun WorkoutUiState.shareMovement(): ExerciseRep? =
    workoutShareMovement(config, asksSnatches = snatches != null, flowRep = flow?.primaryRep)

/** Whether [state] recorded enough heart-rate samples to draw the share card's HR telemetry panel. */
internal val WorkoutUiState.hasShareHeartChart: Boolean
    get() = heartRateSamples?.count { it in HeartRate.BPM_RANGE }?.let { it >= 2 } == true

/** Share's interactive sheet for the finished workout summary: its [SummaryShareCard] to send as PNG or text. */
@Composable
internal fun SummaryShareSheet(
    state: WorkoutUiState,
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
        hasHeartRate = state.hasShareHeartChart,
        hasWeekBadge = state.week != null,
        hasMovement = state.shareMovement() != null,
    ) { config ->
        SummaryShareCard(state, config)
    }
}

/**
 * The finished workout as a printed telemetry card: Done (or Ended), its name and shape, its week standing
 * badge, optional movement silhouette watermark, timeline waveform, heart-rate zone-banding chart, and clean
 * tabular Barlow Condensed stats.
 */
@Composable
internal fun SummaryShareCard(state: WorkoutUiState, config: ShareCardConfig = ShareCardConfig()) {
    val counted = if (state.caps.isNotEmpty()) {
        state.capRounds.toString()
    } else {
        stringResource(Res.string.workout_stat_rounds_value, state.countDone, state.countOf)
    }
    val peakBpm = state.heartRateSamples?.filter { it in HeartRate.BPM_RANGE }?.maxOrNull()
        ?: state.heartRate?.peakBpm?.takeIf { it in HeartRate.BPM_RANGE }
    val stats = buildList {
        add(ShareStat(formatClock(state.elapsedSeconds), stringResource(Res.string.workout_stat_time)))
        if (state.workSeconds > 0) {
            add(ShareStat(formatClock(state.workSeconds), stringResource(Res.string.workout_stat_work)))
        }
        if (peakBpm != null) {
            add(
                ShareStat(
                    value = peakBpm.toString(),
                    label = stringResource(Res.string.workout_stat_peak_bpm),
                    unit = stringResource(CoreRes.string.heart_rate_unit),
                    accent = true,
                ),
            )
        }
        if (size < MAX_SUMMARY_SHARE_STATS) {
            add(
                ShareStat(
                    counted,
                    stringResource(
                        if (state.flow != null) Res.string.workout_stat_moves else Res.string.workout_stat_rounds,
                    ),
                ),
            )
        }
        if (size < MAX_SUMMARY_SHARE_STATS) {
            state.calories?.let {
                add(
                    ShareStat(
                        value = it.toString(),
                        label = stringResource(Res.string.workout_stat_calories),
                        unit = stringResource(Res.string.workout_stat_kcal_unit),
                    ),
                )
            }
        }
    }
    WorkoutShareCard(
        eyebrow = stringResource(if (state.ended) Res.string.workout_ended else Res.string.workout_done),
        title = presetNameOrCustom(state.presetName),
        subtitle = workoutDescription(state.config),
        config = state.config,
        stats = stats,
        week = state.week,
        heartRateSamples = state.heartRateSamples,
        heartRateMarks = state.liveMarks,
        maxHeartRate = state.maxHeartRate,
        movement = state.shareMovement(),
        cardConfig = config,
    )
}

private const val MAX_SUMMARY_SHARE_STATS = 4
