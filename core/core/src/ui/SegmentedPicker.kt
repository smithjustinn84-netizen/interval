package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection

/**
 * One of a few [options] as a segmented control: the chosen option is an elevated pill
 * ([IntervalPalette.segmentSelected]) on the recessed track ([IntervalPalette.segmentTrack]), with unselected labels
 * dimmed so the active choice reads at a glance in both light and dark themes. The pill slides to a newly chosen
 * option, its check growing in once it has settled under it. With [selected] null, or not among them, none is chosen
 * and no pill shows. Many options on a narrow row ([check] off, two [maxLines]) leave the check out and tighten
 * spacing rather than losing words; a label on one line beside another on two sits in the middle of its pill.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    check: Boolean = true,
    maxLines: Int = 1,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val compact = options.size >= COMPACT_SEGMENTS
    val horizontalPadding = if (compact) Spacing.space4 else Spacing.space8
    val textStyle = if (compact && maxLines > 1) IntervalTheme.type.caption else IntervalTheme.type.label
    // The pill is drawn, not laid out, at the option's index along the row: it needs no size of its own.
    val index = options.indexOf(selected)
    val slide by animateFloatAsState(index.coerceAtLeast(0).toFloat(), settleSpring(), label = "indicator")
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.full)
            .background(palette.segmentTrack)
            .border(Sizes.hairline, palette.line, Corners.full)
            .padding(Spacing.space4)
            .drawBehind {
                if (index < 0) return@drawBehind
                val gap = Spacing.space4.toPx()
                val width = (size.width - gap * (options.size - 1)) / options.size
                val visualSlide = if (layoutDirection == LayoutDirection.Rtl) options.lastIndex - slide else slide
                drawPill(Offset((width + gap) * visualSlide, 0f), Size(width, size.height), palette)
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val textColor = animateColorAsState(
                if (isSelected) palette.text else palette.textDim,
                tween(Motion.STATE_MILLIS),
                label = "segmentText",
            )
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.segment)
                    .clip(Corners.full)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    }
                    .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    check && isSelected,
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
                // The colour as a producer, read as the text is drawn: a change of pick fades it without
                // composing the segment at every frame. Material's Text would read it in composition.
                BasicText(
                    label(option),
                    style = LocalTextStyle.current.merge(textStyle).merge(TextStyle(textAlign = TextAlign.Center)),
                    color = { textColor.value },
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The chosen option's pill at [topLeft], filled and edged. */
private fun DrawScope.drawPill(topLeft: Offset, pillSize: Size, palette: IntervalPalette) {
    val radius = CornerRadius(pillSize.height / 2f)
    drawRoundRect(palette.segmentSelected, topLeft = topLeft, size = pillSize, cornerRadius = radius)
    drawRoundRect(
        palette.line,
        topLeft = topLeft,
        size = pillSize,
        cornerRadius = radius,
        style = Stroke(width = Sizes.hairline.toPx()),
    )
}

/** At five or more segments across a phone, tighten padding and use caption text so labels stay balanced. */
private const val COMPACT_SEGMENTS = 5
