package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing

/**
 * A still of [demo] at the end of a list row, where the figure holds its shape best: decoration beside the row's
 * words, which name the lift, so a screen reader hears nothing of it.
 */
@Composable
fun FigureThumb(demo: ExerciseDemo, modifier: Modifier = Modifier) {
    // Drawn in round the figure: in the view a whole rep takes, a deep squat's still is a speck in a thumb.
    val still = remember(demo) { demo.closeUp() }
    val palette = IntervalTheme.palette
    ExerciseFigure(
        still,
        progress = { still.still },
        contentDescription = null,
        modifier = modifier
            .size(Sizes.figureThumbWidth, Sizes.figureThumbHeight)
            .clip(Corners.radius12)
            .background(palette.surface)
            .border(Sizes.hairline, palette.line, Corners.radius12)
            .padding(Spacing.space4),
    )
}
