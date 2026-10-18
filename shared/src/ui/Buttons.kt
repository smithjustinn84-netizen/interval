package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.timer_start_workout
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The one raised, accent-filled action of a screen: Start workout. Its shadow is the accent itself,
 * so the button glows rather than sits in a grey smudge.
 */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    val glow = palette.accent.copy(alpha = Opacity.START_SHADOW)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(Sizes.start)
            .pressScale(interaction)
            .shadow(elevation = Sizes.startElevation, shape = shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(palette.accent)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            IntervalIcons.Play,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(Sizes.playIcon),
        )
        Text(
            stringResource(Res.string.timer_start_workout),
            style = IntervalTheme.type.value,
            color = palette.onAccent,
        )
    }
}

/**
 * A stepper button that repeats while held: one step on tap, then a fast run after a short hold.
 * Every step ticks under the thumb, so a held run can be counted without looking.
 */
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
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onStep()
    }
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
            .surfaceButton(interaction, Corners.radius12, palette.surfaceVariant, enabled = enabled, onClick = {
                if (!repeated) step()
                repeated = false
            })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = IntervalTheme.type.control,
            color = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED_GLYPH),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** One of a dialog's equal-width actions: the confirming one in accent, the other on a quiet fill. */
@Composable
fun RowScope.DialogButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    ModalButton(
        text = text,
        onClick = onClick,
        primary = primary,
        style = IntervalTheme.type.control,
        padding = Spacing.space12,
        modifier = modifier.weight(1f),
    )
}

/** A full-width accent button for a sheet's confirming action. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ModalButton(
        text = text,
        onClick = onClick,
        primary = true,
        style = IntervalTheme.type.value,
        padding = Spacing.space24,
        modifier = modifier,
    )
}

/** A full-width button on a quiet fill for a sheet's second action, under the accent one. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ModalButton(
        text = text,
        onClick = onClick,
        primary = false,
        style = IntervalTheme.type.label,
        padding = Spacing.space24,
        modifier = modifier,
    )
}

/** A borderless action in the dim ink, for the quiet choices of a sheet: Cancel, Edit, Delete. */
@Composable
fun QuietTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .surfaceButton(interaction, Corners.radius12, Color.Transparent, onClick)
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.label, color = IntervalTheme.palette.textDim)
    }
}

/** The button recipe dialogs and sheets share: accent when [primary], a quiet fill otherwise. */
@Composable
private fun ModalButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    style: TextStyle,
    padding: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val fill = if (primary) palette.accent else palette.surfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.save)
            .surfaceButton(interaction, Corners.radius18, fill, onClick)
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = if (primary) palette.onAccent else palette.text)
    }
}

/**
 * What every tappable surface shares: it shrinks while pressed ([pressScale]), is clipped to [shape] on
 * [fill], and takes the tap with the app's indication, as [role]. Size, border and contents are the
 * caller's.
 */
@Composable
fun Modifier.surfaceButton(
    interaction: MutableInteractionSource,
    shape: Shape,
    fill: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
): Modifier = pressScale(interaction)
    .clip(shape)
    .background(fill)
    .clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
