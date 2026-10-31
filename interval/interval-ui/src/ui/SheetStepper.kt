package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * A sheet's one value, big between − and + (hold either to repeat), over a [hint] on how it steps. [text]
 * shows the [value], rolling up or down the way it moved; [onStep] takes -1 or 1. A [label], such as the
 * value's unit, sits under it.
 */
@Composable
fun SheetStepper(
    value: Int,
    text: String,
    lessDescription: String,
    moreDescription: String,
    canLower: Boolean,
    canRaise: Boolean,
    onStep: (Int) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IntervalTheme.type.display,
    color: Color = IntervalTheme.palette.text,
    label: String? = null,
) {
    val palette = IntervalTheme.palette
    val rising = rememberRising(value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RepeatButton(
                "−",
                lessDescription,
                enabled = canLower,
                onStep = { onStep(-1) },
                modifier = Modifier.size(IntervalSizes.sheetStepper),
            )
            Box(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                RollingText(text, rising, style = style.copy(textAlign = TextAlign.Center), color = color)
            }
            RepeatButton(
                "+",
                moreDescription,
                enabled = canRaise,
                onStep = { onStep(1) },
                modifier = Modifier.size(IntervalSizes.sheetStepper),
            )
        }
        label?.let { Text(it, style = IntervalTheme.type.section, color = palette.textDim) }
        Text(
            hint,
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}
