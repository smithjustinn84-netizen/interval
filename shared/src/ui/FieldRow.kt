package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format

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
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = if (compact) Sizes.listRow else Sizes.intervalRow)
            .clickable(role = Role.Button, onClickLabel = "Edit ${field.label.lowercase()}", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.space10 else Spacing.space14),
    ) {
        Box(Modifier.width(Sizes.stripe).fillMaxHeight().background(field.stripe()))
        Column(Modifier.weight(1f).padding(vertical = if (compact) Spacing.space6 else Spacing.space10)) {
            Text(field.label, style = IntervalTheme.type.cardTitle, color = palette.text)
            Text(field.hint, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        if (compact) {
            ValuePill(
                text,
                color = field.valueColor(),
                tint = field.tint(),
                height = Sizes.valuePillSmall,
                style = IntervalTheme.type.stepperValue,
            )
        } else {
            ValuePill(
                if (field == ConfigField.Rounds) "×$text" else text,
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
