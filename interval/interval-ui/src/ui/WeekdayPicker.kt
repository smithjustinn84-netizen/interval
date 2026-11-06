package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.timer.DAYS_PER_WEEK

/**
 * Monday to Sunday as toggles over [weekdays], a bit per day (Monday is bit 0; see [weekdayMask]). Unless
 * [allowNone], at least one day stays picked, for a caller that needs somewhere to put what it plans.
 */
@Composable
fun WeekdayPicker(
    weekdays: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        for (weekday in 1..DAYS_PER_WEEK) {
            val bit = 1 shl (weekday - 1)
            DayToggle(
                label = weekdayText(weekday, WeekdayForm.Initial),
                description = weekdayText(weekday),
                on = weekdays and bit != 0,
                onToggle = { onChange((weekdays xor bit).takeIf { it != 0 || allowNone } ?: weekdays) },
                role = Role.Checkbox,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** These weekdays (1 for Monday) as [WeekdayPicker] holds them: a bit per day. */
fun weekdayMask(weekdays: Set<Int>): Int = weekdays.filter { it in 1..DAYS_PER_WEEK }.sumOf { 1 shl (it - 1) }

/** The weekdays (1 for Monday) a [WeekdayPicker]'s [mask] has picked. */
fun weekdaysOf(mask: Int): Set<Int> = (1..DAYS_PER_WEEK).filter { mask and (1 shl (it - 1)) != 0 }.toSet()

/** A day to pick, read out as [description]: accent when [on], following its state's colour. */
@Composable
fun DayToggle(
    label: String,
    description: String,
    on: Boolean,
    onToggle: () -> Unit,
    role: Role,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (on) palette.accent else palette.surfaceVariant, tween(Motion.STATE_MILLIS))
    val ink by animateColorAsState(if (on) palette.onAccent else palette.text, tween(Motion.STATE_MILLIS))
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.segment)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(fill)
            .toggleable(on, interaction, LocalIndication.current, role = role) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggle()
            }
            .semantics { contentDescription = description }
            .padding(vertical = Spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IntervalTheme.type.label, color = ink, textAlign = TextAlign.Center)
    }
}
