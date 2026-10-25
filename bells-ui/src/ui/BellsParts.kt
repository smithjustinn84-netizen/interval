package dev.justinsmith.bells.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.UnknownExercise
import dev.justinsmith.bells.ui.resources.Res
import dev.justinsmith.bells.ui.resources.common_joined
import dev.justinsmith.bells.ui.resources.exercise_unknown
import dev.justinsmith.bells.ui.resources.lift_double
import dev.justinsmith.bells.ui.resources.record_tag
import dev.justinsmith.bells.ui.resources.session_title_more
import dev.justinsmith.bells.ui.resources.session_title_two
import dev.justinsmith.bells.ui.resources.side_left
import dev.justinsmith.bells.ui.resources.side_right
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.rememberRising
import dev.justinsmith.interval.ui.surfaceButton
import org.jetbrains.compose.resources.stringResource

/** An exercise's name: "Two-hand swing". */
@Composable
fun exerciseName(exercise: Exercise): String = when (exercise) {
    is BuiltInExercise -> stringResource(exercise.label())
    is CustomExercise -> exercise.name
    is UnknownExercise -> stringResource(Res.string.exercise_unknown)
}

/**
 * A lift's name, saying when it is done with two bells: "Press · Two bells". A lift only done with two, like
 * the Armor Building Complex, says so in its name already.
 */
@Composable
fun liftName(exercise: Exercise, double: Boolean): String = if (double && exercise.doubles == Doubles.Optional) {
    stringResource(Res.string.common_joined, exerciseName(exercise), stringResource(Res.string.lift_double))
} else {
    exerciseName(exercise)
}

@Composable
fun sideName(side: Side): String = stringResource(
    if (side ==
        Side.Left
    ) {
        Res.string.side_left
    } else {
        Res.string.side_right
    },
)

/**
 * A session's name from the lifts in it, in the order done: "Press", "Two-hand swing and Get-up",
 * "Clean and 2 more". Empty for a session with none.
 */
@Composable
fun sessionTitle(exercises: List<Exercise>): String = when (exercises.size) {
    0 -> ""
    1 -> exerciseName(exercises[0])
    2 -> stringResource(Res.string.session_title_two, exerciseName(exercises[0]), exerciseName(exercises[1]))
    else -> stringResource(Res.string.session_title_more, exerciseName(exercises[0]), exercises.size - 1)
}

/**
 * One of a set of choices laid out in a wrapping row: selected, it takes the accent's border and tint,
 * both following the state rather than snapping. A pick ticks; a [Role.Checkbox] answers as a switch.
 */
@Composable
fun Choice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (selected) palette.accentTint else palette.surfaceVariant, spec, label = "fill")
    val edge by animateColorAsState(if (selected) palette.accent else palette.surfaceVariant, spec, label = "edge")
    val haptic = when {
        role != Role.Checkbox -> HapticFeedbackType.SegmentTick
        selected -> HapticFeedbackType.ToggleOff
        else -> HapticFeedbackType.ToggleOn
    }
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .surfaceButton(interaction, Corners.radius12, fill, onClick = onClick, role = role, haptic = haptic)
            .border(Sizes.border, edge, Corners.radius12)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.label, color = palette.text)
    }
}

/** The tag on a set, or a record on the summary, that beat a record standing: "Record" on the accent's tint. */
@Composable
fun RecordTag(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Text(
        stringResource(Res.string.record_tag),
        style = IntervalTheme.type.caption,
        color = palette.accent,
        modifier = modifier
            .clip(Corners.full)
            .background(palette.accentTint)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    )
}

/** [Choice]s for each of [options], wrapping onto as many lines as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        options.forEach { option -> Choice(label(option), option == selected, onClick = { onSelect(option) }) }
    }
}

/**
 * A figure with its caption on a card: "Time 14:32". Given the number behind it as [rank], the figure rolls
 * to each new value; a figure that ticks every second (Time) passes none and simply changes.
 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, rank: Int? = null) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
    ) {
        Text(label, style = IntervalTheme.type.caption, color = palette.textDim)
        if (rank != null) {
            RollingText(value, rising = rememberRising(rank), style = IntervalTheme.type.stat, color = palette.text)
        } else {
            Text(value, style = IntervalTheme.type.stat, color = palette.text)
        }
    }
}
