package dev.justinsmith.lift.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.session_rest
import dev.justinsmith.lift.session.resources.session_rest_less_cd
import dev.justinsmith.lift.session.resources.session_rest_more_cd
import dev.justinsmith.lift.session.resources.session_rest_skip
import org.jetbrains.compose.resources.stringResource

/**
 * The rest after a set, counting down just above Log set while it runs, where the hand is: what is left, −/+
 * to make it and every rest after shorter or longer, Skip, and a bar running out along its foot. It opens as a
 * rest starts and closes as it ends, keeping its last words while it goes.
 */
@Composable
internal fun RestBand(rest: RestState?, onStep: (Int) -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(rest) }
    if (rest != null) shown = rest
    AnimatedVisibility(rest != null, modifier, enter = enterExpanding(), exit = exitShrinking()) {
        shown?.let { RestBandContent(it, onStep, onSkip) }
    }
}

@Composable
private fun RestBandContent(rest: RestState, onStep: (Int) -> Unit, onSkip: () -> Unit) {
    val palette = IntervalTheme.palette
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
                Text(
                    stringResource(Res.string.session_rest),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(formatClock(rest.secondsLeft), style = IntervalTheme.type.stat, color = palette.text)
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.timelineMini)
                .background(palette.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(1f - rest.progress)
                    .height(Sizes.timelineMini)
                    .background(palette.restMark),
            )
        }
    }
}
