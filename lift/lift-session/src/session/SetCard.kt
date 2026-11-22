package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.BesideOrUnder
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalWeightUnit
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.longestWordWidth
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.faults
import dev.justinsmith.lift.domain.stepped
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.next_change
import dev.justinsmith.lift.session.resources.next_figure_cd
import dev.justinsmith.lift.session.resources.next_how_to
import dev.justinsmith.lift.session.resources.next_how_to_cd
import dev.justinsmith.lift.session.resources.next_last_time
import dev.justinsmith.lift.session.resources.next_watch_for
import dev.justinsmith.lift.ui.LoadStepper
import dev.justinsmith.lift.ui.demo.ExerciseBand
import dev.justinsmith.lift.ui.demo.rep
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.loadLine
import dev.justinsmith.lift.ui.platesLine
import dev.justinsmith.lift.ui.setLine
import dev.justinsmith.lift.ui.words
import org.jetbrains.compose.resources.stringResource

// The pieces a card about one set shares: Up next on a program day, and Next set in a free session.

/**
 * A figure doing the lift, a band across the top of its card: it runs the same kinematic animation as the exercise's
 * page in clean compact form, which a tap on it opens ([onHowTo], by the exercise's key). As a record lands
 * ([celebrating], the set's id while its moment is on) the figure raises what it holds, or takes a bow.
 */
@Composable
internal fun NextFigure(
    exercise: Exercise,
    double: Boolean,
    onHowTo: (String) -> Unit,
    celebrating: Long? = null,
) {
    val rep = exercise.rep ?: return
    ExerciseBand(
        rep = rep,
        contentDescription = stringResource(Res.string.next_figure_cd, liftName(exercise, double)),
        onClick = { onHowTo(exercise.key) },
        onClickLabel = stringResource(Res.string.next_how_to),
        celebrating = celebrating,
    )
}

/**
 * The worst fault the lift is prone to, a line under its figure ("Watch for · Hips back, shins stay vertical"): the
 * first of those its rests say, set by set. Nothing for a lift with none, any but a ballistic or hinge built-in.
 */
@Composable
internal fun FaultsNote(exercise: Exercise) {
    val worst = exercise.faults.firstOrNull() ?: return
    Text(
        stringResource(Res.string.next_watch_for, stringResource(worst.words())),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12),
    )
}

/**
 * The lift's name, large, with How to for its page ([onHowTo], by the exercise's key) and, when [onChange] is
 * given, Change to pick another lift.
 */
@Composable
internal fun LiftHeading(
    exercise: Exercise,
    double: Boolean,
    onHowTo: (String) -> Unit,
    onChange: (() -> Unit)? = null,
) {
    val name = liftName(exercise, double)
    val howTo = stringResource(Res.string.next_how_to_cd, name)
    // How to and Change drop under the name when beside it a word of the name would break.
    BesideOrUnder(
        main = {
            Text(
                name,
                style = IntervalTheme.type.section,
                color = IntervalTheme.palette.text,
                modifier = Modifier.semantics { heading() },
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietTextButton(
                    stringResource(Res.string.next_how_to),
                    { onHowTo(exercise.key) },
                    Modifier.semantics { contentDescription = howTo },
                )
                onChange?.let { QuietTextButton(stringResource(Res.string.next_change), it) }
            }
        },
        gap = Spacing.space4,
        mainMinWidth = longestWordWidth(name, IntervalTheme.type.section),
    )
}

/**
 * A set's load, large, with Lighter and Heavier a bell, the bar's smallest plates or a band either way among
 * what the user has ([SessionUiState.bells], [SessionUiState.bar], [SessionUiState.bands]); the bar adds its
 * plates a side. Draws nothing with no load to show: the mat, or a band not yet known.
 */
@Composable
internal fun LoadStep(load: Load, state: SessionUiState, onStep: (Int) -> Unit) {
    val unit = LocalWeightUnit.current
    val line = loadLine(load) ?: return
    val canStep = { step: Int -> load.stepped(step, state.bells, state.bar, state.bands, unit) != null }
    LoadStepper(
        line,
        canLower = canStep(-1),
        canRaise = canStep(1),
        onStep = onStep,
        note = (load as? Load.Bar)?.let { platesLine(it.kg, state.bar, unit) },
        compact = true,
    )
}

/** "Last time · 16 kg × 8", or nothing the first time ([last] null). */
@Composable
internal fun LastTimeLine(last: SetLog?) {
    if (last == null) return
    Text(
        stringResource(Res.string.next_last_time, setLine(last)),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
    )
}
