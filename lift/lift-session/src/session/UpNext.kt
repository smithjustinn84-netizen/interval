package dev.justinsmith.lift.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.fadeThroughContent
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.hold_time_it
import dev.justinsmith.lift.session.resources.next_change
import dev.justinsmith.lift.session.resources.next_day_done
import dev.justinsmith.lift.session.resources.next_plan
import dev.justinsmith.lift.session.resources.next_progress
import dev.justinsmith.lift.session.resources.next_skip
import dev.justinsmith.lift.session.resources.next_title
import dev.justinsmith.lift.session.resources.notice_block_skipped
import dev.justinsmith.lift.session.resources.session_undo
import dev.justinsmith.lift.ui.countLine
import dev.justinsmith.lift.ui.liftName
import org.jetbrains.compose.resources.stringResource

/**
 * In a program day: the next planned set, its load a tap a step lighter or heavier for the rest of its block,
 * and what was lifted last time; the session's foot logs the set or starts its EMOM. How to opens the exercise's
 * page; Change opens its block to change for today, Skip skips the rest of it, and Today's plan lists every block.
 */
@Composable
internal fun UpNextCard(
    state: SessionUiState,
    actions: SessionActions,
    onChange: (Int) -> Unit,
    onPlan: () -> Unit,
    onTimeIt: (() -> Unit)? = null,
) {
    val palette = IntervalTheme.palette
    // Each set logged fades the card through to the next; the last fades it through to the day's done.
    AnimatedContent(
        targetState = state.upNext,
        transitionSpec = { fadeThroughContent() },
        contentKey = { it?.number ?: NO_NEXT },
        label = "upNext",
    ) { next ->
        if (next == null) {
            Column {
                if (state.dayDone) {
                    Text(
                        stringResource(Res.string.next_day_done),
                        style = IntervalTheme.type.body,
                        color = palette.textDim,
                        modifier = Modifier.padding(vertical = Spacing.space8),
                    )
                    if (state.plan.any { it.open }) QuietTextButton(stringResource(Res.string.next_plan), onPlan)
                }
            }
        } else {
            NextSetCard(next, state, actions, onChange, onPlan, onTimeIt)
        }
    }
}

/** The key of [UpNextCard]'s content once nothing is next. */
private const val NO_NEXT = -1

@Composable
private fun NextSetCard(
    next: UpNext,
    state: SessionUiState,
    actions: SessionActions,
    onChange: (Int) -> Unit,
    onPlan: () -> Unit,
    onTimeIt: (() -> Unit)?,
) {
    val palette = IntervalTheme.palette
    val notices = LocalNotices.current
    val set = next.set
    val undo = stringResource(Res.string.session_undo)
    val skipped = stringResource(Res.string.notice_block_skipped, liftName(set.exercise, set.double))
    ListCard(Modifier.padding(top = Spacing.space8)) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(
                    Res.string.common_joined,
                    stringResource(Res.string.next_title),
                    stringResource(Res.string.next_progress, next.number, next.total),
                ),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            LiftHeading(set.exercise, set.double, actions.onOpenExercise)
            NextLoad(next, state, actions)
            LastTimeLine(next.lastTime)
            if (set.held && onTimeIt != null) {
                SecondaryButton(
                    stringResource(Res.string.hold_time_it),
                    onTimeIt,
                    Modifier.fillMaxWidth().padding(top = Spacing.space4),
                )
            }
            Row(
                Modifier.padding(top = Spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                SecondaryButton(stringResource(Res.string.next_change), { onChange(next.block) }, Modifier.weight(1f))
                SecondaryButton(
                    stringResource(Res.string.next_skip),
                    {
                        val blockIndex = next.block
                        actions.onSkipBlock(blockIndex)
                        notices.show(skipped, NoticeAction(undo) { actions.onUnskipBlock(blockIndex) })
                    },
                    Modifier.weight(1f),
                )
            }
            QuietTextButton(
                stringResource(Res.string.next_plan),
                onPlan,
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * The next set's load, large, with Lighter and Heavier a step either way ([LoadStep]), then how much of it:
 * "× 10 · Left". With no load to change, the mat or a band not yet known, only how much.
 */
@Composable
private fun NextLoad(next: UpNext, state: SessionUiState, actions: SessionActions) {
    val set = next.set
    LoadStep(next.bellKg, set.exercise.equipment, state, actions.onStepLoad, double = set.double, band = next.band)
    Text(
        countLine(if (set.held) 0 else set.reps, set.side, set.reps.takeIf { set.held }),
        style = IntervalTheme.type.value,
        color = IntervalTheme.palette.text,
    )
}
