package dev.justinsmith.interval.workout

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.SECONDS_PER_MINUTE
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.workout_cap_best
import dev.justinsmith.interval.workout.resources.workout_cap_pace
import dev.justinsmith.interval.workout.resources.workout_cap_round_time
import dev.justinsmith.interval.workout.resources.workout_cap_rounds
import dev.justinsmith.interval.workout.resources.workout_round_done
import dev.justinsmith.interval.workout.resources.workout_summary_cap
import dev.justinsmith.interval.workout.resources.workout_summary_cap_best
import dev.justinsmith.interval.workout.resources.workout_summary_cap_pace
import dev.justinsmith.interval.workout.resources.workout_undo_round
import dev.justinsmith.interval.workout.resources.workout_undo_round_cd
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// A time cap on the workout screen: the rounds done in it, rolling up as each is tapped, the best of the same workout
// under them and the pace of the rounds so far, then one big Round done. Undo stands where Back does, which a cap
// has none of.

/**
 * The rounds done in the cap, "14 rounds", rolling up as one is done; "Best: 15" under them once one was counted
 * before; then how long the round under way has run and the average round, "This round 1:12 · 1:25 a round".
 */
@Composable
internal fun CapTally(cap: CapNow, modifier: Modifier = Modifier, centered: Boolean = true) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        RollingText(
            pluralStringResource(Res.plurals.workout_cap_rounds, cap.rounds, cap.rounds),
            rising = true,
            style = IntervalTheme.type.stat,
            color = IntervalTheme.workout.onGround,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        cap.best?.let { best ->
            Text(
                stringResource(Res.string.workout_cap_best, best),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.workout.onGroundDim,
                textAlign = align,
            )
        }
        capPaceText(cap)?.let { pace ->
            Text(pace, style = IntervalTheme.type.label, color = IntervalTheme.workout.onGroundDim, textAlign = align)
        }
    }
}

/** "This round 1:12 · 1:25 a round", or the round's time alone before the first is done; null outside the work. */
@Composable
private fun capPaceText(cap: CapNow): String? {
    val round = cap.roundSeconds ?: return null
    val pace = cap.paceSeconds
    return if (pace == null) {
        stringResource(Res.string.workout_cap_round_time, formatClock(round))
    } else {
        stringResource(Res.string.workout_cap_pace, formatClock(round), formatClock(pace))
    }
}

/**
 * The cap's one big target, filled with the phase colour ([primaryFill]): a round is done. Live only in the cap's
 * work while the timer runs; it answers with a confirming tap under the thumb.
 */
@Composable
internal fun RoundDoneButton(
    state: WorkoutUiState,
    primaryFill: () -> Color,
    onFinishRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = state.cap?.live == true && state.running
    val fill = primaryFill()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = IntervalSizes.roundDone)
            .surfaceButton(
                interaction,
                Corners.full,
                if (live) fill else fill.copy(alpha = Opacity.DISABLED),
                onFinishRound,
                enabled = live,
                haptic = HapticFeedbackType.Confirm,
            )
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.workout_round_done),
            style = IntervalTheme.type.value,
            color = IntervalTheme.workout.onFill(fill),
        )
    }
}

/** Takes back the last round done in the cap, a tap by mistake; in Back's place, which a cap has none of. */
@Composable
internal fun UndoRoundButton(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    BigButton(
        stringResource(Res.string.workout_undo_round),
        onUndo,
        modifier,
        description = stringResource(Res.string.workout_undo_round_cd),
        haptic = HapticFeedbackType.SegmentTick,
    )
}

/**
 * A workout of time caps: each cap's rounds in its minutes, "14 rounds in 20 min" ("New best: 15 rounds in 20 min"
 * when it beats every one before), with its pace under it, "1:25 a round · fastest 1:10 · slowest 1:41"; then the
 * workout's name and shape.
 */
@Composable
internal fun CapSummaryLines(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        state.caps.forEach { result ->
            val score = result.score
            val minutes = score.capSeconds / SECONDS_PER_MINUTE
            Text(
                when {
                    // Ended before the cap was up: its rounds, not a claim on its minutes.
                    state.ended -> pluralStringResource(Res.plurals.workout_cap_rounds, score.rounds, score.rounds)

                    result.newBest ->
                        pluralStringResource(Res.plurals.workout_summary_cap_best, score.rounds, score.rounds, minutes)

                    else -> pluralStringResource(Res.plurals.workout_summary_cap, score.rounds, score.rounds, minutes)
                },
                style = IntervalTheme.type.section,
                color = IntervalTheme.workout.onGround,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.space8),
            )
            val pace = score.paceMillis
            val fastest = score.fastestMillis
            val slowest = score.slowestMillis
            if (pace != null && fastest != null && slowest != null) {
                Text(
                    stringResource(
                        Res.string.workout_summary_cap_pace,
                        formatClock((pace / MILLIS_PER_SECOND).toInt()),
                        formatClock((fastest / MILLIS_PER_SECOND).toInt()),
                        formatClock((slowest / MILLIS_PER_SECOND).toInt()),
                    ),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.workout.onGround,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            shapeText(state),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.workout.onGroundDim,
            textAlign = TextAlign.Center,
        )
    }
}
