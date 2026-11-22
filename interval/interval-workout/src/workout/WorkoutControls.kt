package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WorkoutColors
import dev.justinsmith.interval.ui.fadeThroughContent
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_add_time
import dev.justinsmith.interval.workout.resources.workout_add_time_cd
import dev.justinsmith.interval.workout.resources.workout_back
import dev.justinsmith.interval.workout.resources.workout_back_cd
import dev.justinsmith.interval.workout.resources.workout_end
import dev.justinsmith.interval.workout.resources.workout_end_cd
import dev.justinsmith.interval.workout.resources.workout_pause
import dev.justinsmith.interval.workout.resources.workout_resume
import dev.justinsmith.interval.workout.resources.workout_skip
import dev.justinsmith.interval.workout.resources.workout_skip_cd
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

/** Skip goes on to the next interval: at the foot, opposite Back, its arrow pointing on. */
@Composable
internal fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    StepButton(
        stringResource(Res.string.workout_skip),
        IntervalIcons.Chevron,
        iconFirst = false,
        onSkip,
        stringResource(Res.string.workout_skip_cd),
        modifier,
    )
}

/**
 * Back restarts the interval, or goes to the one before near its start (see [WorkoutEngine.back]). Its arrow points
 * back along the workout, beside Pause and opposite Skip, so it never reads as leaving the workout.
 */
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    StepButton(
        stringResource(Res.string.workout_back),
        IntervalIcons.ChevronBack,
        iconFirst = true,
        onBack,
        stringResource(Res.string.workout_back_cd),
        modifier,
    )
}

/**
 * Back or Skip: a [BigButton] with its arrow before or after its word, or with large text its arrow alone, larger,
 * so Pause between them keeps its word whole ("Pau / se"). Its [description] says it either way.
 */
@Composable
private fun StepButton(
    text: String,
    icon: ImageVector,
    iconFirst: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val ink = IntervalTheme.workout.onGround
    BigButton(
        onClick,
        modifier.semantics { contentDescription = description },
        haptic = HapticFeedbackType.SegmentTick,
    ) {
        Row(
            Modifier.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            if (isLargeText()) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(Sizes.icon))
            } else {
                val arrow = @Composable { Icon(icon, null, tint = ink, modifier = Modifier.size(Sizes.chipIcon)) }
                if (iconFirst) arrow()
                Text(text, style = IntervalTheme.type.control, color = ink)
                if (!iconFirst) arrow()
            }
        }
    }
}

/** Ten seconds more for the interval under way: a pill at the top, as End is, away from the controls at the foot. */
@Composable
internal fun AddTimePill(onAddTime: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        text = stringResource(Res.string.workout_add_time, WorkoutEngine.ADD_TIME_MILLIS / MILLIS_PER_SECOND),
        onClick = onAddTime,
        description = stringResource(Res.string.workout_add_time_cd),
        modifier = modifier,
        haptic = HapticFeedbackType.SegmentTick,
    )
}

@Composable
internal fun BottomControls(
    state: WorkoutUiState,
    primaryFill: () -> Color,
    actions: WorkoutActions,
) {
    val height = Sizes.control
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        // Back and Skip either side of Pause, the way through the workout under the thumb; a time cap runs as it was
        // set, so Undo takes Back's place. While running a cap, Round done is the sole phase-coloured primary button;
        // Pause steps back to a secondary control fill so tired thumbs never confuse the two.
        val side = Modifier.widthIn(min = Sizes.controlSide).heightIn(min = height)
        val inCap = state.cap?.live == true
        val secondaryFill = IntervalTheme.workout.control
        val pauseFill = if (inCap && state.running) ({ secondaryFill }) else primaryFill
        if (inCap) UndoRoundButton(actions.onUndoRound, side) else BackButton(actions.onBack, side)
        PrimaryButton(state, pauseFill, actions.onTogglePause, Modifier.weight(1f).heightIn(min = height))
        SkipButton(actions.onSkip, side)
    }
}

/**
 * Pause / Resume, filled with the phase colour ([primaryFill]) under [WorkoutColors.onFill]'s label. Paused, the fill
 * is [WorkoutColors.onGround] instead: a label doesn't read on the paused grey. The label fades through
 * to its other word, and the tap answers like a switch: off to pause, on to resume.
 */
@Composable
internal fun PrimaryButton(
    state: WorkoutUiState,
    primaryFill: () -> Color,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = primaryFill()
    val content = IntervalTheme.workout.onFill(fill)
    BigButton(
        onTogglePause,
        modifier,
        background = fill,
        haptic = if (state.running) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
        shape = Corners.full,
    ) {
        AnimatedContent(state.running, transitionSpec = { fadeThroughContent() }, label = "pauseResume") { running ->
            Text(
                stringResource(if (running) Res.string.workout_pause else Res.string.workout_resume),
                style = IntervalTheme.type.value,
                color = content,
            )
        }
    }
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = IntervalTheme.workout.control,
    content: Color = IntervalTheme.workout.onGround,
    style: TextStyle = IntervalTheme.type.control,
    description: String? = null,
    haptic: HapticFeedbackType? = null,
    shape: Shape = Corners.radius18,
) {
    BigButton(
        onClick,
        modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        background,
        haptic,
        shape,
    ) {
        Text(text, style = style, color = content)
    }
}

/** A [BigButton] whose face is the caller's: a label that changes in place, say. */
@Composable
internal fun BigButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = IntervalTheme.workout.control,
    haptic: HapticFeedbackType? = null,
    shape: Shape = Corners.radius18,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val workout = IntervalTheme.workout
    val outline = if (background == workout.control) {
        Modifier.border(Sizes.hairline, workout.upcoming, shape)
    } else {
        Modifier
    }
    Box(
        modifier
            .surfaceButton(interaction, shape, background, onClick, haptic = haptic)
            .then(outline)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    haptic: HapticFeedbackType? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val workout = IntervalTheme.workout
    Box(
        modifier
            .sizeIn(minWidth = Sizes.pillWidth, minHeight = Sizes.pillHeight)
            .surfaceButton(interaction, Corners.full, workout.control, onClick, haptic = haptic)
            .border(Sizes.hairline, workout.upcoming, Corners.full)
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IntervalTheme.type.control,
            color = IntervalTheme.workout.onGround,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
