package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.valueOf
import org.jetbrains.compose.resources.stringResource

/**
 * The warm-up and the cool-down side by side, under a shape's rows (or other [fields], such as the sets beside
 * them): each a quiet card on the surface, its name small over its value, which is dim while it is off ("Off",
 * or one set). The sets say the rest between them beside their count once it has one: "2 · 1m". Nothing here
 * takes a colour, so the rows above keep theirs. Tapping one opens its edit sheet through [onEdit].
 */
@Composable
fun BookendRow(
    config: IntervalConfig,
    onEdit: (ConfigField) -> Unit,
    modifier: Modifier = Modifier,
    fields: List<ConfigField> = ConfigField.bookends,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        fields.forEach { field ->
            val value = config.valueOf(field)
            val text = if (field == ConfigField.Sets && config.sets > 1 && config.setRestSeconds > 0) {
                joined(listOf(field.valueText(value), shortDurationText(config.setRestSeconds)))
            } else {
                field.valueText(value)
            }
            // One set is the plain workout: shown as quietly as a bookend that is off.
            val off = field.isOff(value) || (field == ConfigField.Sets && value == 1)
            BookendChip(field, text, off, onClick = { onEdit(field) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookendChip(
    field: ConfigField,
    text: String,
    off: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val valueColor by animateColorAsState(if (off) palette.textDim else palette.text, spec, label = "value")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .heightIn(min = Sizes.listRow)
            .surfaceButton(
                interaction,
                Corners.radius12,
                palette.surface,
                onClick = onClick,
                onClickLabel = stringResource(field.editRes),
            )
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(field.labelRes),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text,
            style = IntervalTheme.type.value,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
