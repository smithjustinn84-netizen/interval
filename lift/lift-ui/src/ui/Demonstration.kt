package dev.justinsmith.lift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.lift.ui.demo.DemoTimeline
import dev.justinsmith.lift.ui.demo.ExerciseDemo
import dev.justinsmith.lift.ui.demo.ExerciseFigure
import dev.justinsmith.lift.ui.demo.FigureColors
import dev.justinsmith.lift.ui.demo.rememberDemoPlayer
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.demo
import dev.justinsmith.lift.ui.resources.demo_cue_jump
import dev.justinsmith.lift.ui.resources.demo_each_rep
import dev.justinsmith.lift.ui.resources.demo_pause
import dev.justinsmith.lift.ui.resources.demo_play
import dev.justinsmith.lift.ui.resources.demo_set_up
import dev.justinsmith.lift.ui.resources.demo_set_up_jump
import dev.justinsmith.lift.ui.resources.demo_timeline
import org.jetbrains.compose.resources.stringResource

/**
 * A figure doing the exercise over and over, its rep laid out on a timeline under it and its [cues] under that,
 * each lit while the figure does what it says: all in one card. Dragging along the timeline or tapping a cue
 * stops the figure there. Pause and Play sit in its corner, since moving content that runs on is something a
 * user must be able to stop; with reduced motion it stands still and offers neither, though it still moves where
 * it is dragged. An exercise's page shows it, and a flow's move that is one of Lift's exercises.
 */
@Composable
fun Demonstration(demo: ExerciseDemo, name: String, cues: List<String>, modifier: Modifier = Modifier) {
    val player = rememberDemoPlayer(demo)
    val colors = FigureColors.theme()
    // Read as the phase changes, not every frame the figure moves.
    val phase by remember(demo, player) { derivedStateOf { demo.phaseAt(player.progress) } }
    val still = prefersReducedMotion()
    ListCard(modifier, fill = IntervalTheme.palette.surfaceVariant) {
        Box(Modifier.fillMaxWidth()) {
            ExerciseFigure(
                demo,
                player,
                contentDescription = stringResource(Res.string.demo, name),
                modifier = Modifier.fillMaxWidth().height(Sizes.figurePage).padding(Spacing.space8),
                colors = colors,
            )
            if (!still) {
                QuietTextButton(
                    stringResource(if (player.playing) Res.string.demo_pause else Res.string.demo_play),
                    onClick = player::toggle,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        DemoTimeline(
            demo,
            player,
            label = stringResource(Res.string.demo_timeline),
            modifier = Modifier.padding(horizontal = Spacing.space16),
            colors = colors,
        )
        val setUp = demo.setUp?.cue?.takeIf { it in cues.indices }

        @Composable
        fun cue(i: Int) = CueRow(
            cues[i],
            current = i == phase.cue,
            lit = colors.of(phase.beat),
            onShow = when {
                i == setUp && still -> { -> player.seek(SET_UP_STILL) }
                i == setUp -> player::replaySetUp
                else -> demo.startOf(i)?.let { start -> { player.seek(start) } }
            },
            showLabel = stringResource(
                if (i == setUp) Res.string.demo_set_up_jump else Res.string.demo_cue_jump,
            ),
        )
        if (setUp == null) {
            cues.indices.forEach { i ->
                RowDivider()
                cue(i)
            }
        } else {
            CueGroupLabel(stringResource(Res.string.demo_set_up))
            cue(setUp)
            CueGroupLabel(stringResource(Res.string.demo_each_rep))
            cues.indices.filter { it != setUp }.forEachIndexed { n, i ->
                if (n > 0) RowDivider()
                cue(i)
            }
        }
    }
}

/** What the cues under it are in a figure's card: the set-up, or each rep. */
@Composable
private fun CueGroupLabel(text: String) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12),
    )
}

/**
 * A cue under a figure: a bar beside it in [lit] while it is [current], dim otherwise; tapping it ([showLabel])
 * moves the figure to where the cue starts, through [onShow], when the figure has a stretch for it.
 */
@Composable
private fun CueRow(line: String, current: Boolean, lit: Color, onShow: (() -> Unit)?, showLabel: String) {
    val palette = IntervalTheme.palette
    val bar by animateColorAsState(if (current) lit else palette.line, label = "cue bar")
    val ink by animateColorAsState(if (current) palette.text else palette.textDim, label = "cue ink")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onShow == null) {
                    Modifier
                } else {
                    Modifier.surfaceButton(
                        interaction,
                        shape = RectangleShape,
                        fill = Color.Transparent,
                        onClick = onShow,
                        onClickLabel = showLabel,
                        haptic = HapticFeedbackType.SegmentTick,
                    )
                },
            )
            .semantics { selected = current }
            .height(IntrinsicSize.Min)
            .padding(Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.width(LiftSizes.cueBar).fillMaxHeight().clip(Corners.full).background(bar))
        Text(line, style = IntervalTheme.type.body, color = ink)
    }
}

/** With reduced motion a picked set-up shows still, halfway through it. */
private const val SET_UP_STILL = -0.5f
