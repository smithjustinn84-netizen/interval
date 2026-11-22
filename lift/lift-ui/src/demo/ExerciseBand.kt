package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import dev.justinsmith.interval.ui.DemoDisplayMode
import dev.justinsmith.interval.ui.ExerciseDemoCanvas
import dev.justinsmith.interval.ui.ExerciseDemoColors
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.rememberRepPlayer
import dev.justinsmith.interval.ui.surfaceButton

/**
 * Shared compact animated band for an [ExerciseRep], used on active Lift session cards and Mobility workout
 * screens: renders the polished organic figure with active motion trail and ground shadow ([DemoDisplayMode.Compact]),
 * free of detail-page plumb lines or scrubber controls. Supports pausing (for a paused mobility workout) and
 * one-shot PR [celebrating] gestures (for Lift sessions).
 */
@Composable
fun ExerciseBand(
    rep: ExerciseRep,
    contentDescription: String?,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    celebrating: Any? = null,
    shape: Shape = RectangleShape,
    fill: Color = IntervalTheme.palette.surfaceVariant,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
) {
    val player = rememberRepPlayer(rep)
    LaunchedEffect(player, paused) {
        if (paused) player.pause() else player.play()
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.figureBand)
            .surfaceButton(
                interaction,
                shape = shape,
                fill = fill,
                onClick = onClick,
                onClickLabel = onClickLabel,
            ),
    ) {
        ExerciseDemoCanvas(
            rep = rep,
            modifier = Modifier.fillMaxSize().padding(Spacing.space8),
            progress = player.heldAt,
            colors = colors,
            contentDescription = contentDescription,
            position = player.position,
            mode = DemoDisplayMode.Compact,
            celebrating = celebrating,
        )
    }
}
