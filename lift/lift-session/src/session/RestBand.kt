package dev.justinsmith.lift.session

import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.LayoutDirection
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.session_rest
import dev.justinsmith.lift.session.resources.session_rest_heart_down
import dev.justinsmith.lift.session.resources.session_rest_heart_high
import dev.justinsmith.lift.session.resources.session_rest_heart_ready
import dev.justinsmith.lift.session.resources.session_rest_less_cd
import dev.justinsmith.lift.session.resources.session_rest_more_cd
import dev.justinsmith.lift.session.resources.session_rest_skip
import org.jetbrains.compose.resources.stringResource

/**
 * The rest after a set, counting down just above Log set while it runs, where the hand is: what is left, −/+
 * to make it and every rest after shorter or longer, Skip, and a bar running out along its foot. It opens as a
 * rest starts and closes as it ends, the last frame it drew shrinking away (`CapturedAnimatedVisibility`). Its
 * last seconds pop in the rest colour with a tick under the thumb, in step with the beeps, as the workout's clock
 * does.
 */
@Composable
internal fun RestBand(
    rest: RestState?,
    onStep: (Int) -> Unit,
    onSkip: () -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    heart: RestHeart? = null,
) {
    CapturedAnimatedVisibility(rest != null, modifier, enter = enterExpanding(), exit = exitShrinking()) {
        rest?.let { RestBandContent(it, heart, onStep, onSkip, progress) }
    }
}

/**
 * The heart rate beside the rest's name and what it says of the next set: on its way down, ready (in the rest's
 * colour, with a tick under the thumb as it turns), or still high. It advises; the clock is the lifter's.
 */
@Composable
private fun RestHeartLine(heart: RestHeart) {
    val palette = IntervalTheme.palette
    val ready = heart.standing == RestHeart.Standing.Ready
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(ready) { if (ready) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    val ink by animateColorAsState(
        if (ready) palette.restMark else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "restHeart",
    )
    Text(
        stringResource(
            when (heart.standing) {
                RestHeart.Standing.ComingDown -> Res.string.session_rest_heart_down
                RestHeart.Standing.Ready -> Res.string.session_rest_heart_ready
                RestHeart.Standing.StillHigh -> Res.string.session_rest_heart_high
            },
            heart.bpm,
        ),
        style = IntervalTheme.type.caption,
        color = ink,
        maxLines = 1,
    )
}

@Composable
private fun RestBandContent(
    rest: RestState,
    heart: RestHeart?,
    onStep: (Int) -> Unit,
    onSkip: () -> Unit,
    progress: () -> Float,
) {
    val palette = IntervalTheme.palette
    val countingDown = rest.secondsLeft in 1..RestClock.COUNTDOWN
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(rest.secondsLeft) {
        if (countingDown) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface),
    ) {
        Row(
            Modifier.padding(
                start = Spacing.space16,
                end = Spacing.space4,
                top = Spacing.space8,
                bottom = Spacing.space8,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    Text(
                        stringResource(Res.string.session_rest),
                        style = IntervalTheme.type.caption,
                        color = palette.textDim,
                    )
                    heart?.let { RestHeartLine(it) }
                }
                CountdownClock(
                    formatClock(rest.secondsLeft),
                    intervalKey = Unit,
                    countingDown = countingDown,
                    accent = palette.restMark,
                    style = IntervalTheme.type.stat,
                    ink = palette.text,
                )
            }
            RepeatButton(
                "−",
                stringResource(Res.string.session_rest_less_cd),
                enabled = rest.seconds > RestClock.MIN_SECONDS,
                onStep = { onStep(-1) },
            )
            RepeatButton(
                "+",
                stringResource(Res.string.session_rest_more_cd),
                enabled = rest.seconds < RestClock.MAX_SECONDS,
                onStep = { onStep(1) },
            )
            QuietTextButton(stringResource(Res.string.session_rest_skip), onSkip)
        }
        // The bar runs out from the end edge, drawn rather than laid out: it moves every tick.
        val mark = palette.restMark
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(Sizes.timelineMini)
                .background(palette.surfaceVariant)
                .drawBehind {
                    val left = size.width * (1f - progress())
                    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - left else 0f
                    drawRect(mark, topLeft = Offset(start, 0f), size = Size(left, size.height))
                },
        )
    }
}

@PreviewFormFactors
@Composable
private fun RestBandHeartPreview() {
    val rests = listOf(
        RestState(90, 71, 0.2f) to RestHeart(156, RestHeart.Standing.StillHigh),
        RestState(90, 44, 0.5f) to RestHeart(138, RestHeart.Standing.ComingDown),
        RestState(90, 12, 0.85f) to RestHeart(112, RestHeart.Standing.Ready),
    )
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.background).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            rests.forEach { (rest, heart) ->
                RestBandContent(rest, heart, onStep = {}, onSkip = {}, progress = { rest.progress })
            }
        }
    }
}
