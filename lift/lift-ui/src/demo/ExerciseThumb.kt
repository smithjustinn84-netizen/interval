package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.DemoDisplayMode
import dev.justinsmith.interval.ui.ExerciseDemoCanvas
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing

/**
 * A static close-up still of [rep] at the end of a list row ([DemoDisplayMode.Thumb]): decoration beside the row's
 * words, which name the exercise, so a screen reader hears nothing of it.
 */
@Composable
fun ExerciseThumb(rep: ExerciseRep, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ExerciseDemoCanvas(
        rep = rep,
        modifier = modifier
            .size(Sizes.figureThumbWidth, Sizes.figureThumbHeight)
            .clip(Corners.radius12)
            .background(palette.surface)
            .border(Sizes.hairline, palette.line, Corners.radius12)
            .padding(Spacing.space4),
        progress = rep.still,
        contentDescription = null,
        mode = DemoDisplayMode.Thumb,
        tracksView = false,
    )
}
