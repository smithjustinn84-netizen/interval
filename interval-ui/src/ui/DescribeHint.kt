package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A dim line under a sheet's field: examples to try, or a note on what the words left out. */
@Composable
fun DescribeHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.space4),
    )
}
