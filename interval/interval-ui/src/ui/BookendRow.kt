package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.valueOf
import org.jetbrains.compose.resources.stringResource

/**
 * The warm-up and the cool-down side by side, under a shape's rows: each a small card with its name and value
 * ("Off" for none), on its colour's wash while it is on. Tapping one opens its edit sheet through [onEdit].
 */
@Composable
fun BookendRow(config: IntervalConfig, onEdit: (ConfigField) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        ConfigField.bookends.forEach { field ->
            BookendChip(field, config.valueOf(field), onClick = { onEdit(field) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookendChip(field: ConfigField, value: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val off = field.isOff(value)
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (off) palette.surface else field.tint(), spec, label = "fill")
    val valueColor by animateColorAsState(if (off) palette.textDim else field.valueColor(), spec, label = "value")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = Sizes.listRow)
            .surfaceButton(
                interaction,
                Corners.radius12,
                fill,
                onClick = onClick,
                onClickLabel = stringResource(field.editRes),
            )
            .border(Sizes.border, palette.line, Corners.radius12)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        StripeMark(field.stripe(), height = Sizes.icon)
        Text(
            stringResource(field.labelRes),
            style = IntervalTheme.type.label,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
        Text(field.valueText(value), style = IntervalTheme.type.value, color = valueColor)
    }
}
