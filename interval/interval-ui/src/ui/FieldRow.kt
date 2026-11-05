package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format
import org.jetbrains.compose.resources.stringResource

/**
 * One value of the workout: its phase stripe, name and hint, and the value in a pill of its colour with
 * a chevron; tapping opens its edit sheet. [compact] is the shorter row of a settings list.
 */
@Composable
fun FieldRow(
    field: ConfigField,
    value: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val text = field.format(value)
    val editLabel = stringResource(field.editRes)
    val stripe = field.stripe()
    val gap = if (compact) Spacing.space12 else Spacing.space16
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) Sizes.listRow else IntervalSizes.intervalRow)
            .phaseStripe(stripe)
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = editLabel,
                onClick = onClick,
            )
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .padding(start = IntervalSizes.stripe + gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Column(Modifier.weight(1f).padding(vertical = if (compact) Spacing.space8 else Spacing.space12)) {
            Text(stringResource(field.labelRes), style = IntervalTheme.type.label, color = palette.text)
            Text(stringResource(field.hintRes), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (compact) {
            ValuePill(
                text,
                color = field.valueColor(),
                tint = field.tint(),
                height = IntervalSizes.valuePillSmall,
                style = IntervalTheme.type.value,
            )
        } else {
            ValuePill(
                field.displayText(value),
                color = field.valueColor(),
                tint = field.tint(),
            )
        }
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.padding(end = Spacing.space12).size(Sizes.icon),
        )
    }
}

/**
 * A setting that isn't one of the workout's values, shown as the compact [FieldRow] shows one: its [title] and
 * [hint], the [value] in a pill and a chevron. Tapping opens what changes it, which [onClickLabel] names. [dim]
 * for a value that says the setting is off.
 */
@Composable
fun ValueRow(
    title: String,
    hint: String?,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    dim: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .padding(start = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.space8)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            if (hint != null) Text(hint, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        ValuePill(
            value,
            color = if (dim) palette.textDim else palette.text,
            tint = palette.surfaceVariant,
            height = IntervalSizes.valuePillSmall,
            style = IntervalTheme.type.value,
        )
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.padding(end = Spacing.space12).size(Sizes.icon),
        )
    }
}

/**
 * A field card's phase colour down its start edge. Drawn rather than laid out, so it stays on the card's
 * edge while the content shrinks under a press; the content is padded clear of it by [IntervalSizes.stripe].
 */
fun Modifier.phaseStripe(color: Color): Modifier = drawBehind {
    val stripeWidth = IntervalSizes.stripe.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - stripeWidth else 0f
    drawRect(color, topLeft = Offset(x, 0f), size = Size(stripeWidth, size.height))
}

/** A phase's mark beside a heading or a row: a rounded stripe of [color], [height] tall. */
@Composable
fun StripeMark(color: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(IntervalSizes.stripe, height).clip(Corners.full).background(color))
}
