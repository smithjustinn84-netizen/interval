package dev.justinsmith.lift.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.exercise_figure
import dev.justinsmith.lift.library.resources.exercise_figure_change
import dev.justinsmith.lift.library.resources.exercise_figure_hint
import dev.justinsmith.lift.library.resources.exercise_figure_none
import dev.justinsmith.lift.ui.demo.FigureThumb
import dev.justinsmith.lift.ui.demo.demo
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.stringResource

/**
 * The built-in an exercise of the user's is shown by, among [options] (those done with its equipment), or none:
 * the one [chosen] in a row, which Change opens on the rest, each by its still; a pick closes them again.
 */
@Composable
internal fun FigureChoice(
    chosen: BuiltInExercise?,
    options: List<BuiltInExercise>,
    onChoose: (BuiltInExercise?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        GroupLabel(stringResource(Res.string.exercise_figure))
        ListCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .padding(start = Spacing.space16, end = Spacing.space8)
                    .padding(vertical = Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(figureName(chosen), style = IntervalTheme.type.label, color = palette.text)
                    Text(
                        stringResource(Res.string.exercise_figure_hint),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
                chosen?.demo?.let { FigureThumb(it) }
                QuietTextButton(stringResource(Res.string.exercise_figure_change), { open = !open }, expanded = open)
            }
            AnimatedVisibility(open, enter = enterExpanding(), exit = exitShrinking()) {
                Column(Modifier.selectableGroup()) {
                    (listOf(null) + options).forEach { option ->
                        RowDivider()
                        FigureRow(option, selected = option == chosen) {
                            onChoose(option)
                            open = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun figureName(figure: BuiltInExercise?): String =
    figure?.let { exerciseName(it) } ?: stringResource(Res.string.exercise_figure_none)

@Composable
private fun FigureRow(figure: BuiltInExercise?, selected: Boolean, onPick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton, onClick = onPick)
            .pressScale(interaction)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Text(figureName(figure), style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
        figure?.demo?.let { FigureThumb(it) }
        // Room for the check on every row, so the stills line up.
        Box(Modifier.size(Sizes.icon)) {
            if (selected) Icon(IntervalIcons.Check, contentDescription = null, tint = palette.accent)
        }
    }
}
