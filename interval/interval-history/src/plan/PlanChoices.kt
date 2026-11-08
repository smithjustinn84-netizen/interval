package dev.justinsmith.interval.plan

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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.plan_on_timer
import dev.justinsmith.interval.history.resources.plan_sport_name_label
import dev.justinsmith.interval.history.resources.plan_sport_name_placeholder
import dev.justinsmith.interval.history.resources.plan_sport_text
import dev.justinsmith.interval.history.resources.plan_sport_which
import dev.justinsmith.interval.history.resources.plan_workout
import dev.justinsmith.interval.history.resources.sport_other
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

// What the add sheet's and the log sheet's lists are made of, and their Sport side.

/** The interval workouts to pick from, the [picked] one checked; nothing to pick with only one. */
@Composable
internal fun IntervalChoiceList(choices: List<PlanChoice>, picked: Int, onPick: (Int) -> Unit) {
    if (choices.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Caption(stringResource(Res.string.plan_workout))
        ListCard(Modifier.selectableGroup()) {
            choices.forEachIndexed { index, choice ->
                if (index > 0) RowDivider()
                ChoiceRow(choice, selected = index == picked, onPick = { onPick(index) })
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: PlanChoice, selected: Boolean, onPick: () -> Unit) {
    val shape = workoutDescription(choice.preset.config)
    PickRow(
        name = if (choice.onTimer) stringResource(Res.string.plan_on_timer) else choice.preset.name,
        detail = if (choice.onTimer) stringResource(Res.string.common_joined, choice.preset.name, shape) else shape,
        selected = selected,
        onPick = onPick,
    )
}

/**
 * The Sport side: the sports named, the [picked] one checked, then one of the user's own, whose [name] is typed
 * under the list once it is the pick.
 */
@Composable
internal fun SportChoiceList(picked: Int, name: TextFieldState, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Caption(stringResource(Res.string.plan_sport_which))
        ListCard(Modifier.selectableGroup()) {
            Sport.named.forEachIndexed { index, sport ->
                if (index > 0) RowDivider()
                PickRow(sportName(sport), detail = null, selected = index == picked, onPick = { onPick(index) })
            }
            RowDivider()
            PickRow(
                stringResource(Res.string.sport_other),
                detail = null,
                selected = picked >= Sport.OTHER,
                onPick = { onPick(Sport.OTHER) },
            )
        }
        if (picked >= Sport.OTHER) {
            NameField(
                name,
                placeholder = stringResource(Res.string.plan_sport_name_placeholder),
                label = stringResource(Res.string.plan_sport_name_label),
                maxLength = Sport.NAME_MAX_LENGTH,
                capitalization = KeyboardCapitalization.Words,
            )
        }
        Text(
            stringResource(Res.string.plan_sport_text),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
        )
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
}

/** One choice of a group: its [name] over a line of [detail], if it has one, checked when [selected]. */
@Composable
internal fun PickRow(name: String, detail: String?, selected: Boolean, onPick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton, onClick = onPick)
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
            if (selected) Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            if (detail != null) Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}
