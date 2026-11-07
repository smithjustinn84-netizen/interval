package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.lift.ui.LiftSizes

/**
 * [demo]'s rep laid out as a bar, a stretch for each phase in its beat's colour, with a marker where [player] is.
 * Tapping or dragging along it stops the figure there, so a rep can be gone through by hand; a screen reader
 * moves it as it would a slider, and the arrow keys step it from one stretch to the next. [label] names it.
 * The marker is read while drawing, so a running figure moves it without recomposing.
 */
@Composable
fun DemoTimeline(
    demo: ExerciseDemo,
    player: DemoPlayer,
    label: String,
    modifier: Modifier = Modifier,
    colors: FigureColors = FigureColors.theme(),
) {
    val marker = IntervalTheme.palette.text
    val stretches = demo.stretches
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Sizes.iconButton)
            .pointerInput(demo, player) { detectTapGestures { player.seek(it.x / size.width) } }
            .pointerInput(demo, player) {
                detectHorizontalDragGestures(onDragStart = { player.seek(it.x / size.width) }) { change, _ ->
                    change.consume()
                    player.seek(change.position.x / size.width)
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val starts = stretches.map { it.first }
                val to = when (event.key) {
                    Key.DirectionRight -> starts.firstOrNull { it > player.progress + STEP_SLACK } ?: starts.first()
                    Key.DirectionLeft -> starts.lastOrNull { it < player.progress - STEP_SLACK } ?: starts.last()
                    else -> return@onKeyEvent false
                }
                player.seek(to)
                true
            }
            .focusable()
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(player.progress, 0f..1f)
                setProgress { at ->
                    player.seek(at)
                    true
                }
            },
    ) {
        val track = Sizes.progressBar.toPx()
        val gap = LiftSizes.demoStretchGap.toPx()
        val middle = size.height / 2
        val round = CornerRadius(track / 2)
        stretches.forEach { (start, phase) ->
            val from = start * size.width + gap / 2
            val to = phase.until.coerceAtMost(1f) * size.width - gap / 2
            drawRoundRect(
                colors.of(phase.beat),
                topLeft = Offset(from, middle - track / 2),
                size = Size((to - from).coerceAtLeast(0f), track),
                cornerRadius = round,
            )
        }
        val markerWidth = LiftSizes.demoMarkerWidth.toPx()
        val markerHeight = LiftSizes.demoMarkerHeight.toPx()
        val x = (player.progress * size.width).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
        drawRoundRect(
            marker,
            topLeft = Offset(x - markerWidth / 2, middle - markerHeight / 2),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerWidth / 2),
        )
    }
}

/** A step by key goes to a stretch's start beyond this close to where it is, so it never stays put. */
private const val STEP_SLACK = 0.001f
