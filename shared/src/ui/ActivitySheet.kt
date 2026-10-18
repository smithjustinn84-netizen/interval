package dev.justinsmith.interval.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.activity_barbell
import dev.justinsmith.interval.resources.activity_barbell_hint
import dev.justinsmith.interval.resources.activity_bike
import dev.justinsmith.interval.resources.activity_bike_hint
import dev.justinsmith.interval.resources.activity_bodyweight
import dev.justinsmith.interval.resources.activity_bodyweight_hint
import dev.justinsmith.interval.resources.activity_climbing
import dev.justinsmith.interval.resources.activity_climbing_hint
import dev.justinsmith.interval.resources.activity_general
import dev.justinsmith.interval.resources.activity_general_hint
import dev.justinsmith.interval.resources.activity_jump_rope
import dev.justinsmith.interval.resources.activity_jump_rope_hint
import dev.justinsmith.interval.resources.activity_kettlebell
import dev.justinsmith.interval.resources.activity_kettlebell_hint
import dev.justinsmith.interval.resources.activity_rower
import dev.justinsmith.interval.resources.activity_rower_hint
import dev.justinsmith.interval.resources.activity_running
import dev.justinsmith.interval.resources.activity_running_hint
import dev.justinsmith.interval.resources.activity_sheet_note
import dev.justinsmith.interval.resources.activity_ski_erg
import dev.justinsmith.interval.resources.activity_ski_erg_hint
import dev.justinsmith.interval.resources.activity_title
import dev.justinsmith.interval.timer.Activity
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Picks what the work intervals are spent doing, which the calorie estimate depends on: one row per
 * [Activity], the [selected] one checked. A pick applies at once and closes the sheet.
 */
@Composable
fun ActivitySheet(selected: Activity, onSelect: (Activity) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    IntervalSheet(title = stringResource(Res.string.activity_title), onDismiss = onDismiss) { hide ->
        Text(stringResource(Res.string.activity_title), style = IntervalTheme.type.value, color = palette.text)
        Text(stringResource(Res.string.activity_sheet_note), style = IntervalTheme.type.meta, color = palette.textDim)
        ListCard(Modifier.selectableGroup()) {
            Activity.entries.forEachIndexed { index, activity ->
                if (index > 0) RowDivider()
                ActivityRow(
                    activity,
                    selected = activity == selected,
                    onClick = {
                        onSelect(activity)
                        hide()
                    },
                )
            }
        }
    }
}

/** One choice of [ActivitySheet]: its name and what it covers, with a check when it is the one chosen. */
@Composable
private fun ActivityRow(activity: Activity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .pressScale(interaction)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(activity.labelRes), style = IntervalTheme.type.label, color = palette.text)
            Text(stringResource(activity.hintRes), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        val check by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.STATE_MILLIS), label = "check")
        Icon(
            IntervalIcons.Check,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(Sizes.icon).alpha(check),
        )
    }
}

/** How each activity is named wherever it is shown or picked; [Activity.name] is what is stored. */
val Activity.labelRes: StringResource
    get() = when (this) {
        Activity.General -> Res.string.activity_general
        Activity.Bike -> Res.string.activity_bike
        Activity.Rower -> Res.string.activity_rower
        Activity.SkiErg -> Res.string.activity_ski_erg
        Activity.Running -> Res.string.activity_running
        Activity.JumpRope -> Res.string.activity_jump_rope
        Activity.Bodyweight -> Res.string.activity_bodyweight
        Activity.Kettlebell -> Res.string.activity_kettlebell
        Activity.Barbell -> Res.string.activity_barbell
        Activity.Climbing -> Res.string.activity_climbing
    }

/** The line under an activity's name in the picker, saying what it covers. */
private val Activity.hintRes: StringResource
    get() = when (this) {
        Activity.General -> Res.string.activity_general_hint
        Activity.Bike -> Res.string.activity_bike_hint
        Activity.Rower -> Res.string.activity_rower_hint
        Activity.SkiErg -> Res.string.activity_ski_erg_hint
        Activity.Running -> Res.string.activity_running_hint
        Activity.JumpRope -> Res.string.activity_jump_rope_hint
        Activity.Bodyweight -> Res.string.activity_bodyweight_hint
        Activity.Kettlebell -> Res.string.activity_kettlebell_hint
        Activity.Barbell -> Res.string.activity_barbell_hint
        Activity.Climbing -> Res.string.activity_climbing_hint
    }
