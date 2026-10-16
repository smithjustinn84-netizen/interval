package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.config_rounds_short
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
            .heightIn(min = if (compact) Sizes.listRow else Sizes.intervalRow)
            // Drawn, not laid out, so the stripe stays on the card's edge while the content shrinks.
            .drawBehind { drawRect(stripe, size = Size(Sizes.stripe.toPx(), size.height)) }
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = editLabel,
                onClick = onClick,
            )
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .padding(start = Sizes.stripe + gap),
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
                height = Sizes.valuePillSmall,
                style = IntervalTheme.type.value,
            )
        } else {
            ValuePill(
                if (field == ConfigField.Rounds) stringResource(Res.string.config_rounds_short, value) else text,
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
