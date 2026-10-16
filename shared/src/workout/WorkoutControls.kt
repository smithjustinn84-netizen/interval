package dev.justinsmith.interval.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.workout_add_time
import dev.justinsmith.interval.resources.workout_add_time_cd
import dev.justinsmith.interval.resources.workout_back
import dev.justinsmith.interval.resources.workout_back_cd
import dev.justinsmith.interval.resources.workout_end
import dev.justinsmith.interval.resources.workout_end_cd
import dev.justinsmith.interval.resources.workout_pause
import dev.justinsmith.interval.resources.workout_resume
import dev.justinsmith.interval.resources.workout_skip
import dev.justinsmith.interval.resources.workout_skip_cd
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** End pauses the workout and asks before throwing it away (see [WorkoutScreen]). */
@Composable
internal fun EndPill(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        text = stringResource(Res.string.workout_end),
        onClick = onEnd,
        description = stringResource(Res.string.workout_end_cd),
        modifier = modifier,
    )
}

@Composable
internal fun SkipPill(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        text = stringResource(Res.string.workout_skip),
        onClick = onSkip,
        description = stringResource(Res.string.workout_skip_cd),
        modifier = modifier,
    )
}

/** Back restarts the interval, or goes to the one before near its start (see [WorkoutEngine.back]). */
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BigButton(
        stringResource(Res.string.workout_back),
        onBack,
        modifier,
        description = stringResource(Res.string.workout_back_cd),
    )
}

@Composable
internal fun AddTimeButton(onAddTime: () -> Unit, modifier: Modifier = Modifier) {
    BigButton(
        stringResource(Res.string.workout_add_time, WorkoutEngine.ADD_TIME_MILLIS / MILLIS_PER_SECOND),
        onAddTime,
        modifier,
        description = stringResource(Res.string.workout_add_time_cd),
    )
}

@Composable
internal fun BottomControls(
    state: WorkoutUiState,
    primaryFill: Color,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
) {
    val height = Sizes.control
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        BackButton(onBack, Modifier.width(Sizes.controlSide).height(height))
        PrimaryButton(state, primaryFill, onTogglePause, Modifier.weight(1f).height(height))
        AddTimeButton(onAddTime, Modifier.width(Sizes.controlSide).height(height))
    }
}

/**
 * Pause / Resume, filled with the phase colour ([primaryFill]) under an ink label. Paused, the fill
 * is [PhaseColors.onGround] instead: ink doesn't read on the paused grey.
 */
@Composable
internal fun PrimaryButton(
    state: WorkoutUiState,
    primaryFill: Color,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BigButton(
        stringResource(if (state.running) Res.string.workout_pause else Res.string.workout_resume),
        onTogglePause,
        modifier,
        background = primaryFill,
        content = PhaseColors.ink,
        style = IntervalTheme.type.value,
    )
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = PhaseColors.control,
    content: Color = PhaseColors.onGround,
    style: TextStyle = IntervalTheme.type.control,
    description: String? = null,
) {
    Box(
        modifier
            .clip(Corners.radius18)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content)
    }
}

@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .sizeIn(minWidth = Sizes.pillWidth, minHeight = Sizes.pillHeight)
            .clip(Corners.radius12)
            .background(PhaseColors.control)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IntervalTheme.type.control,
            color = PhaseColors.onGround,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
