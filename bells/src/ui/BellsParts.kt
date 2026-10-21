package dev.justinsmith.bells.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.exercise_armor_building_complex
import dev.justinsmith.bells.resources.exercise_clean
import dev.justinsmith.bells.resources.exercise_front_squat
import dev.justinsmith.bells.resources.exercise_get_up
import dev.justinsmith.bells.resources.exercise_goblet_squat
import dev.justinsmith.bells.resources.exercise_halo
import dev.justinsmith.bells.resources.exercise_jerk
import dev.justinsmith.bells.resources.exercise_long_cycle
import dev.justinsmith.bells.resources.exercise_one_hand_swing
import dev.justinsmith.bells.resources.exercise_press
import dev.justinsmith.bells.resources.exercise_row
import dev.justinsmith.bells.resources.exercise_snatch
import dev.justinsmith.bells.resources.exercise_two_hand_swing
import dev.justinsmith.bells.resources.session_title_more
import dev.justinsmith.bells.resources.session_title_two
import dev.justinsmith.bells.resources.side_left
import dev.justinsmith.bells.resources.side_right
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.surfaceButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun Exercise.label(): StringResource = when (this) {
    Exercise.TwoHandSwing -> Res.string.exercise_two_hand_swing
    Exercise.OneHandSwing -> Res.string.exercise_one_hand_swing
    Exercise.Clean -> Res.string.exercise_clean
    Exercise.Press -> Res.string.exercise_press
    Exercise.Snatch -> Res.string.exercise_snatch
    Exercise.GetUp -> Res.string.exercise_get_up
    Exercise.GobletSquat -> Res.string.exercise_goblet_squat
    Exercise.Row -> Res.string.exercise_row
    Exercise.Jerk -> Res.string.exercise_jerk
    Exercise.LongCycle -> Res.string.exercise_long_cycle
    Exercise.Halo -> Res.string.exercise_halo
    Exercise.FrontSquat -> Res.string.exercise_front_squat
    Exercise.ArmorBuildingComplex -> Res.string.exercise_armor_building_complex
}

/** An exercise's name: "Two-hand swing". */
@Composable
fun exerciseName(exercise: Exercise): String = stringResource(exercise.label())

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

/** One of a set of choices laid out in a wrapping row: selected, it takes the accent's border and tint. */
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .surfaceButton(
                interaction,
                Corners.radius12,
                if (selected) palette.accentTint else palette.surfaceVariant,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .border(Sizes.border, if (selected) palette.accent else palette.surfaceVariant, Corners.radius12)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.label, color = palette.text)
    }
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

/** A figure with its caption on a card: "Time 14:32". */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
    ) {
        Text(label, style = IntervalTheme.type.caption, color = palette.textDim)
        Text(value, style = IntervalTheme.type.stat, color = palette.text)
    }
}
