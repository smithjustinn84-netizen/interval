package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledSwitch

/** The label over a group of list rows: "Built-in", "Today", "Cues". */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IntervalTheme.type.label,
        color = IntervalTheme.palette.textDim,
        modifier = modifier
            .padding(start = Spacing.space4, top = Spacing.space12, bottom = Spacing.space8)
            .semantics { heading() },
    )
}

/**
 * A white card holding a group of rows, separated by [RowDivider]s. On a sheet, which is the card's own surface,
 * it takes the [fill] of the sheet's fields instead, so it still stands out.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    fill: Color = IntervalTheme.palette.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(fill),
        content = content,
    )
}

/** The hairline between rows in a [ListCard], inset from the leading edge. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = Spacing.space16), color = IntervalTheme.palette.line)
}

/**
 * A row that leads to another screen: a [title] with an optional [subtitle], and a chevron saying so.
 * The row itself is the button; the chevron is drawn, not read.
 */
@Composable
fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/**
 * A row that switches a setting: a [title], an optional [subtitle] and a switch drawing [checked].
 * When not [enabled] (a setting that cannot apply right now) the whole row dims and takes no touch.
 */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    // The track's colour follows the thumb across, rather than jumping ahead of it.
    val track by animateColorAsState(
        if (checked) palette.accent else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "track",
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
            ) { on ->
                haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle()
            }
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .alpha(if (enabled) 1f else Opacity.DISABLED)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        // The row owns the toggle and its semantics; the switch only draws the state. One thumb, in
        // onAccent, in both states, the track's accent or text-dim saying which, as the design system draws it.
        UnstyledSwitch(checked = checked, onCheckedChange = null) {
            Track(
                Modifier
                    .size(Sizes.switchWidth, Sizes.switchHeight)
                    .clip(Corners.full)
                    .background(track)
                    .padding((Sizes.switchHeight - Sizes.switchThumb) / 2),
            ) {
                Thumb(
                    Modifier
                        .size(Sizes.switchThumb)
                        .clip(Corners.full)
                        .background(palette.onAccent),
                )
            }
        }
    }
}

/**
 * What a list shows before it has anything: an [icon] in a ring of the accent's tint, a [title] and
 * a line of [text] saying what will appear, centred in whatever card holds it. It rises in as the
 * list would have.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    AnimatedVisibility(rememberEntrance(), modifier, enter = risingEnter()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.space24, vertical = Spacing.space32),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            Box(
                Modifier
                    .padding(bottom = Spacing.space12)
                    .size(Sizes.emptyIconRing)
                    .clip(CircleShape)
                    .background(palette.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(Sizes.emptyIcon))
            }
            Text(title, style = IntervalTheme.type.section, color = palette.text, textAlign = TextAlign.Center)
            Text(text, style = IntervalTheme.type.body, color = palette.textDim, textAlign = TextAlign.Center)
        }
    }
}
