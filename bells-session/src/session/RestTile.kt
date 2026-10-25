package dev.justinsmith.bells.session

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.bells.domain.RestClock
import dev.justinsmith.bells.domain.RestState
import dev.justinsmith.bells.session.resources.Res
import dev.justinsmith.bells.session.resources.session_rest
import dev.justinsmith.bells.session.resources.session_rest_less_cd
import dev.justinsmith.bells.session.resources.session_rest_more_cd
import dev.justinsmith.bells.session.resources.session_rest_skip
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The rest after a set, counting down where Since last set stands: what is left, −/+ to make it and every rest
 * after shorter or longer, Skip, and a bar running out under it.
 */
@Composable
fun RestTile(rest: RestState, onStep: (Int) -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.session_rest),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(formatClock(rest.secondsLeft), style = IntervalTheme.type.stat, color = palette.text)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.timelineMini)
                .clip(Corners.full)
                .background(palette.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(1f - rest.progress)
                    .height(Sizes.timelineMini)
                    .clip(Corners.full)
                    .background(palette.restMark),
            )
        }
    }
}
