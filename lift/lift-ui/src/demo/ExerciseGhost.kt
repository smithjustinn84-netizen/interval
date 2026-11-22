package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.ui.DemoDisplayMode
import dev.justinsmith.interval.ui.ExerciseDemoCanvas
import dev.justinsmith.interval.ui.ExerciseDemoColors
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.rememberRepPlayer

/**
 * [rep] done over and over as a ghost beside a running timer ([DemoDisplayMode.Ghost]): the figure and what it
 * holds as one quiet outline, [Sizes.figureGhost] square, with no ground of its own, so it stands on whatever it
 * is laid on (give it [colors] for that ground). It is there to be glanced at, not tapped: the words beside it
 * name the lift, so it is read aloud only when handed a [contentDescription]. [paused] holds it where it is, as a
 * timer that is over does; it stops by itself off screen, and stands still for whoever asks for less motion.
 */
@Composable
fun ExerciseGhost(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    paused: Boolean = false,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
) {
    val player = rememberRepPlayer(rep)
    LaunchedEffect(player, paused) {
        if (paused) player.pause() else player.play()
    }
    ExerciseDemoCanvas(
        rep = rep,
        modifier = modifier.size(Sizes.figureGhost),
        progress = player.heldAt,
        colors = colors,
        contentDescription = contentDescription,
        position = player.position,
        mode = DemoDisplayMode.Ghost,
    )
}

/** Ghosts held partway through a rep, on a surface and on the workout's ground, each over the figure filled in. */
@PreviewFormFactors
@Composable
private fun ExerciseGhostPreview() {
    val reps = listOf(
        KettlebellReps.Snatch,
        KettlebellReps.GobletSquat,
        KettlebellReps.GetUp,
        BarbellBackSquatDemo,
        HaloDemo,
    )
    IntervalTheme {
        Column {
            GhostRows(reps, IntervalTheme.palette.surface, ExerciseDemoColors.theme())
            GhostRows(reps, IntervalTheme.workout.ground, ExerciseDemoColors.workout())
        }
    }
}

@Composable
private fun GhostRows(reps: List<ExerciseRep>, ground: Color, colors: ExerciseDemoColors) {
    Column(
        Modifier.background(ground).padding(Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        listOf(DemoDisplayMode.Ghost, DemoDisplayMode.Compact).forEach { mode ->
            listOf(PREVIEW_EARLY, PREVIEW_LATE).forEach { at ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    reps.forEach { rep ->
                        ExerciseDemoCanvas(
                            rep = rep,
                            modifier = Modifier.size(Sizes.figureGhost),
                            progress = at,
                            colors = colors,
                            mode = mode,
                            tracksView = false,
                        )
                    }
                }
            }
        }
    }
}

private const val PREVIEW_EARLY = 0.3f
private const val PREVIEW_LATE = 0.7f
