package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/**
 * A screen's top bar: an optional navigation action (Back) on the left, actions on the right, and
 * the large title under them, as on iOS large-title navigation bars and Material's large top app bar.
 */
@Composable
fun TitleBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.iconButton)
                .padding(start = Spacing.space4, end = Spacing.space8, top = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation()
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(
            title,
            style = IntervalTheme.type.screenTitle,
            color = IntervalTheme.palette.text,
            modifier = Modifier
                .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space12)
                .semantics { heading() },
        )
    }
}

/** An icon-only top-bar action, with its [description] for screen readers. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = IntervalTheme.palette
    Box(
        modifier
            .size(Sizes.iconButton)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED),
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/** The label over a group of list rows: "Built-in", "Today", "Cues". */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IntervalTheme.type.groupLabel,
        color = IntervalTheme.palette.textDim,
        modifier = modifier
            .padding(start = Spacing.space4, top = Spacing.space12, bottom = Spacing.space8)
            .semantics { heading() },
    )
}

/** A white card holding a group of rows, separated by [RowDivider]s. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(IntervalTheme.palette.surface),
        content = content,
    )
}

/** The hairline between rows in a [ListCard], inset from the leading edge. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = Spacing.space16), color = IntervalTheme.palette.line)
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.space16, vertical = Spacing.space6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.body, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = palette.onAccent,
                checkedBorderColor = palette.accent,
                uncheckedTrackColor = palette.surfaceVariant,
                uncheckedThumbColor = palette.textDim,
                uncheckedBorderColor = palette.textDim,
            ),
        )
    }
}

/** The one raised, accent-filled action of a screen: Start workout. */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    Box(
        modifier
            .height(Sizes.start)
            .shadow(elevation = Sizes.startElevation, shape = shape)
            .clip(shape)
            .background(palette.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Start workout", style = IntervalTheme.type.startLabel, color = palette.onAccent)
    }
}

/** A stepper button that repeats while held: one step on tap, then a fast run after a short hold. */
@Composable
fun RepeatButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // Set once the hold starts repeating, so the click that ends the hold doesn't add one more step.
    // Cleared on every new press or cancelled press: a hold that ends without a click (dragged off,
    // or the field hit its limit) must not swallow the next tap.
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Press || it is PressInteraction.Cancel) repeated = false
        }
    }
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(Motion.STEPPER_HOLD_MILLIS)
            repeated = true
            while (true) {
                step()
                delay(Motion.STEPPER_REPEAT_MILLIS)
            }
        }
    }
    Box(
        modifier
            .size(Sizes.stepper)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (!repeated) step()
                repeated = false
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = IntervalTheme.type.stepperGlyph,
            color = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED_GLYPH),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
