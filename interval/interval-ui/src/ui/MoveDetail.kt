package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.flow_move_time_sides
import dev.justinsmith.interval.ui.resources.move_back_to_moves
import dev.justinsmith.interval.ui.resources.move_held
import dev.justinsmith.interval.ui.resources.move_moved
import dev.justinsmith.interval.ui.resources.move_number
import dev.justinsmith.lift.ui.Demonstration
import dev.justinsmith.lift.ui.cues
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * One move of a flow, a page in place of the flow's on its sheet (or a sheet of its own over the running flow):
 * which move it is and for how long, its name, how it is done in the flow's own words, and, for a move that is one
 * of Lift's exercises, Lift's figure doing it with its cues lit in turn ([Demonstration]). [onBack] returns to the
 * flow, which the back gesture does too.
 */
@Composable
fun MoveDetail(move: Move, number: Int, of: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ModalBackHandler(onBack)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            val time = formatClock(move.seconds)
            Text(
                joined(
                    listOf(
                        stringResource(Res.string.move_number, number, of),
                        if (move.sided) stringResource(Res.string.flow_move_time_sides, time) else time,
                        stringResource(if (move.held) Res.string.move_held else Res.string.move_moved),
                    ),
                ),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(
                move.name,
                style = IntervalTheme.type.value,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (move.cue.isNotBlank()) Text(move.cue, style = IntervalTheme.type.body, color = palette.text)
        val exercise = move.builtIn
        val demo = move.demo
        if (exercise != null && demo != null) {
            Demonstration(demo, move.name, stringArrayResource(exercise.cues()))
        }
        SecondaryButton(stringResource(Res.string.move_back_to_moves), onBack, Modifier.fillMaxWidth())
    }
}
