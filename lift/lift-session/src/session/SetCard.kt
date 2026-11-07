package dev.justinsmith.lift.session

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.surfaceButton
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
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.LoadStepper
import dev.justinsmith.lift.ui.demo.ExerciseFigure
import dev.justinsmith.lift.ui.demo.demo
import dev.justinsmith.lift.ui.demo.rememberDemoPlayer
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.loadLine
import dev.justinsmith.lift.ui.platesLine
import dev.justinsmith.lift.ui.setLine
import dev.justinsmith.lift.ui.words
import org.jetbrains.compose.resources.stringResource

// The pieces a card about one set shares: Up next on a program day, and Next set in a free session.

/**
 * A figure doing the lift, a band across the top of its card: it sets up and then does the lift over and over,
 * as on the exercise's page, which a tap on it opens ([onHowTo], by the exercise's key; the page has Pause). Only a
 * built-in has one: for an exercise of the user's own the card starts with its words.
 */
@Composable
internal fun NextFigure(exercise: Exercise, double: Boolean, onHowTo: (String) -> Unit) {
    val demo = exercise.demo ?: return
    val player = rememberDemoPlayer(demo)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(LiftSizes.nextFigure)
            .surfaceButton(
                interaction,
                shape = RectangleShape,
                fill = IntervalTheme.palette.surfaceVariant,
                onClick = { onHowTo(exercise.key) },
                onClickLabel = stringResource(Res.string.next_how_to),
            ),
    ) {
        ExerciseFigure(
            demo,
            player,
            contentDescription = stringResource(Res.string.next_figure_cd, liftName(exercise, double)),
            modifier = Modifier.fillMaxSize().padding(Spacing.space8),
        )
    }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = IntervalTheme.type.section,
            color = IntervalTheme.palette.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        val howTo = stringResource(Res.string.next_how_to_cd, name)
        QuietTextButton(
            stringResource(Res.string.next_how_to),
            { onHowTo(exercise.key) },
            Modifier.semantics { contentDescription = howTo },
        )
        onChange?.let { QuietTextButton(stringResource(Res.string.next_change), it) }
    }
}

/**
 * A set's load, large, with Lighter and Heavier a bell, the bar's smallest plates or a band either way among
 * what the user has ([SessionUiState.bells], [SessionUiState.bar], [SessionUiState.bands]); the bar adds its
 * plates a side. Draws nothing with no load to show: the mat, or a band not yet known.
 */
@Composable
internal fun LoadStep(load: Load, state: SessionUiState, onStep: (Int) -> Unit) {
    val line = loadLine(load) ?: return
    val canStep = { step: Int -> load.stepped(step, state.bells, state.bar, state.bands) != null }
    LoadStepper(
        line,
        canLower = canStep(-1),
        canRaise = canStep(1),
        onStep = onStep,
        note = (load as? Load.Bar)?.let { platesLine(it.kg, state.bar) },
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
