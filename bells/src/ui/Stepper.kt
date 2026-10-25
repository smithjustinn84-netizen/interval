package dev.justinsmith.bells.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.em
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.Spacing

/**
 * A value, large ([compact]: the size of a tile's number), with − and + that repeat while held, each a step down
 * or up.
 */
@Composable
internal fun Stepper(
    value: String,
    fewer: String,
    more: String,
    canLower: Boolean,
    canRaise: Boolean,
    onStep: (Int) -> Unit,
    compact: Boolean = false,
) {
    val largest = if (compact) IntervalTheme.type.stat else IntervalTheme.type.display
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        // On one line, shrinking to fit: half a sheet on a phone on its side is too narrow for a hold's length
        // at full size. A line height in em, so it shrinks with the font.
        BasicText(
            value,
            Modifier.weight(1f),
            style = largest.copy(color = IntervalTheme.palette.text, lineHeight = 1.em),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = IntervalTheme.type.stat.fontSize,
                maxFontSize = largest.fontSize,
            ),
        )
        RepeatButton("−", fewer, enabled = canLower, onStep = { onStep(-1) })
        RepeatButton("+", more, enabled = canRaise, onStep = { onStep(1) })
    }
}
