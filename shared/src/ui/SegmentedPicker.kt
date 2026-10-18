package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * One of a few [options] as a segmented control: the chosen option is a white card on the variant
 * ground. The card slides to a newly chosen option, its check growing in once it has settled under it.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    // The card is drawn, not laid out, at the option's index along the row: it needs no size of its own.
    val slide by animateFloatAsState(options.indexOf(selected).toFloat(), settleSpring(), label = "indicator")
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surfaceVariant)
            .padding(Spacing.space4)
            .drawBehind {
                val gap = Spacing.space4.toPx()
                val width = (size.width - gap * (options.size - 1)) / options.size
                drawRoundRect(
                    palette.surface,
                    topLeft = Offset((width + gap) * slide, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(Radius.radius12.toPx()),
                )
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.segment)
                    .clip(Corners.radius12)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    },
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    isSelected,
                    enter = fadeIn(tween(Motion.STATE_MILLIS, Motion.STATE_MILLIS)) +
                        expandHorizontally(tween(Motion.STATE_MILLIS, Motion.STATE_MILLIS)),
                    exit = fadeOut(tween(Motion.STATE_MILLIS)) + shrinkHorizontally(tween(Motion.STATE_MILLIS)),
                ) {
                    Icon(
                        IntervalIcons.Check,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                Text(label(option), style = IntervalTheme.type.label, color = palette.text, maxLines = 1)
            }
        }
    }
}
