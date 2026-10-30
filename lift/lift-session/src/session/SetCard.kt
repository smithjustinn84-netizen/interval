package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.steppedBand
import dev.justinsmith.lift.domain.steppedKg
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.next_change
import dev.justinsmith.lift.session.resources.next_how_to
import dev.justinsmith.lift.session.resources.next_how_to_cd
import dev.justinsmith.lift.session.resources.next_last_time
import dev.justinsmith.lift.ui.LoadStepper
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.loadLine
import dev.justinsmith.lift.ui.platesLine
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.stringResource

// The pieces a card about one set shares: Up next on a program day, and Next set in a free session.

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
internal fun LoadStep(
    kg: Double,
    equipment: Equipment,
    state: SessionUiState,
    onStep: (Int) -> Unit,
    double: Boolean = false,
    band: Band? = null,
) {
    val load = loadLine(kg, double, equipment, band) ?: return
    val canStep = { step: Int ->
        if (equipment == Equipment.Band) {
            steppedBand(band, step, state.bands) != null
        } else {
            steppedKg(equipment, kg, step, state.bells, state.bar) != null
        }
    }
    LoadStepper(
        load,
        canLower = canStep(-1),
        canRaise = canStep(1),
        onStep = onStep,
        note = if (equipment == Equipment.Barbell) platesLine(kg, state.bar) else null,
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
