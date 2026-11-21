package dev.justinsmith.interval.workout

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.inUse
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.HeartZoneScale
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LiveHeartTrace
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_heart_show_scale
import dev.justinsmith.interval.workout.resources.workout_heart_show_trace
import org.jetbrains.compose.resources.stringResource

/**
 * The live heart rate under the workout's ring: the zones as a scale, or once tapped the heart rate so far as a
 * line with each work interval shaded, and back with another tap. Both keep the same room, so the ring stays
 * where it is. A tap here turns it and doesn't pause, as one anywhere else on the ring would. Draws nothing
 * without a sensor in use.
 */
@Composable
internal fun WorkoutHeart(state: WorkoutUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.liveHeartRate.inUse) return
    val workout = IntervalTheme.workout
    val turning = modifier
        .widthIn(max = Sizes.zoneScale)
        .fillMaxWidth()
        .clip(Corners.radius18)
        .background(workout.control)
        .border(Sizes.hairline, workout.upcoming, Corners.radius18)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            onClickLabel = stringResource(
                if (state.heartTrace) Res.string.workout_heart_show_scale else Res.string.workout_heart_show_trace,
            ),
            role = Role.Button,
            onClick = onToggle,
        )
        .padding(horizontal = Spacing.space16, vertical = Spacing.space12)
    if (state.heartTrace) {
        LiveHeartTrace(
            state.liveHeartRate,
            state.liveSamples,
            turning,
            maxBpm = state.maxHeartRate,
            marks = state.liveMarks,
        )
    } else {
        HeartZoneScale(state.liveHeartRate, turning, maxBpm = state.maxHeartRate)
    }
}

@PreviewFormFactors
@Composable
private fun WorkoutScreenHeartTracePreview() {
    val samples = listOf(96, 104, 118, 131, 142, 151, 157, 160, 149, 137, 128, 139, 150, 158, 163, 158)
    val marks = listOf(0.1f..0.42f, 0.58f..0.9f)
    WorkoutPreview(
        previewWork.toUiState(running = true).copy(
            liveHeartRate = LiveHeartRateState.Reading(bpm = 158),
            liveSamples = samples,
            liveMarks = marks,
            heartTrace = true,
        ),
        previewWork.progress,
    )
}
