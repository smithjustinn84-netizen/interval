package dev.justinsmith.interval.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.timer.FlowSide
import dev.justinsmith.interval.timer.FlowStep
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MoveFigureBand
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.demo
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.flow_hold
import dev.justinsmith.interval.workout.resources.flow_left_side
import dev.justinsmith.interval.workout.resources.flow_move
import dev.justinsmith.interval.workout.resources.flow_right_side
import dev.justinsmith.interval.workout.resources.flow_switch_sides
import dev.justinsmith.interval.workout.resources.next_last_move
import dev.justinsmith.interval.workout.resources.next_move
import dev.justinsmith.interval.workout.resources.next_right_side
import dev.justinsmith.interval.workout.resources.phase_done
import dev.justinsmith.interval.workout.resources.phase_get_ready
import dev.justinsmith.lift.ui.demo.FigureColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A flow's move over the clock: its name, the side it is on, and how it is done in a line. In a change-over it is
 * the move coming, so there is its whole length to read it in. [centered] over the ring; sideways it leads the
 * words beside it. The side and the cue keep their lines when a move has none, so the ring under them stays put.
 */
@Composable
internal fun FlowHeading(step: FlowStep, accent: Color, modifier: Modifier = Modifier, centered: Boolean = true) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            step.move.name,
            style = IntervalTheme.type.title,
            color = IntervalTheme.workout.onGround,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            step.side?.let { stringResource(sideWord(it)) }.orEmpty(),
            style = IntervalTheme.type.label,
            color = accent,
            textAlign = align,
        )
        Text(
            step.move.cue,
            style = IntervalTheme.type.meta,
            color = IntervalTheme.workout.onGroundDim,
            textAlign = align,
            minLines = CUE_LINES,
            maxLines = CUE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The lines a move's cue is given, used or not. */
private const val CUE_LINES = 2

/**
 * The figure doing the move held, or coming, between the heading and the ring: Lift's, in the workout's colours,
 * holding still while [paused]; a tap opens the move's page ([onOpen]). Its room is kept whenever the flow has a
 * figure to show ([FlowNow.figures]), empty for a move without one, so the ring never moves between moves.
 */
@Composable
internal fun FlowFigure(flow: FlowNow, paused: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val move = flow.step?.move
    val demo = move?.demo
    Box(modifier.fillMaxWidth().height(Sizes.figureBand)) {
        if (move != null && demo != null) {
            MoveFigureBand(
                move,
                demo,
                paused = paused,
                onOpen = onOpen,
                fill = IntervalTheme.workout.control,
                colors = FigureColors.workout(),
            )
        }
    }
}

private fun sideWord(side: FlowSide): StringResource =
    if (side == FlowSide.Left) Res.string.flow_left_side else Res.string.flow_right_side

/**
 * The phase word of a flow: "Hold" (or "Move", for one moved through) while its [step] is done, "Switch sides"
 * between its two, and "Get ready" in the change-over before it.
 */
internal fun flowPhaseWord(phase: Phase, step: FlowStep?): StringResource = when (phase) {
    Phase.Work -> if (step?.move?.held == false) Res.string.flow_move else Res.string.flow_hold
    Phase.Rest -> Res.string.flow_switch_sides
    Phase.Done -> Res.string.phase_done
    else -> Res.string.phase_get_ready
}

/**
 * What follows the move being held: "Next: right side", "Next: Cat-cow", or "Last move". Nothing in a
 * change-over, where the heading already names what is coming.
 */
@Composable
internal fun flowNextText(phase: Phase, flow: FlowNow): String {
    val step = flow.step
    val next = flow.next
    return when {
        phase != Phase.Work || step == null -> ""
        next == null -> stringResource(Res.string.next_last_move)
        next.number == step.number -> stringResource(Res.string.next_right_side)
        else -> stringResource(Res.string.next_move, next.move.name)
    }
}
