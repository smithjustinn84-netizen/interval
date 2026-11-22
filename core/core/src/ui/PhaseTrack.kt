package dev.justinsmith.interval.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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

/**
 * [rep] laid out as a bar, a stretch for each part of it that has a name, the one it is in lit in the accent, with
 * a marker where [progress] says it is. Tapping or dragging along it calls [onScrub] with the place, 0 to 1
 * through the rep, so a rep can be gone through by hand; a screen reader moves it as it would a slider, and the
 * arrow keys step it from one stretch to the next. [label] names it. [progress] is read while drawing, so a
 * running rep moves the marker without recomposing.
 */
@Composable
fun PhaseTrack(
    rep: ExerciseRep,
    progress: () -> Float,
    onScrub: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val scrub by rememberUpdatedState(onScrub)
    val at by rememberUpdatedState(progress)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Sizes.iconButton)
            .pointerInput(rep) { detectTapGestures { scrub(placeAt(it.x, size.width)) } }
            .pointerInput(rep) {
                detectHorizontalDragGestures(onDragStart = { scrub(placeAt(it.x, size.width)) }) { change, _ ->
                    change.consume()
                    scrub(placeAt(change.position.x, size.width))
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // A stretch is stepped to just inside it, where its own name shows.
                val starts = rep.phases.map { it.from + STEP_SLACK }
                val to = when (event.key) {
                    Key.DirectionRight -> starts.firstOrNull { it > at() + STEP_SLACK } ?: starts.first()
                    Key.DirectionLeft -> starts.lastOrNull { it < at() - STEP_SLACK } ?: starts.last()
                    else -> return@onKeyEvent false
                }
                scrub(to)
                true
            }
            .focusable()
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(at(), 0f..1f)
                setProgress { to ->
                    scrub(to.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        val track = Sizes.progressBar.toPx()
        val gap = Sizes.demoStretchGap.toPx()
        val middle = size.height / 2
        val now = at()
        rep.phases.forEach { span ->
            // As [ExerciseRep.phaseAt] has it: a stretch lasts up to and at its end, and the first from the start.
            val lit = now <= span.until && (now > span.from || (now == 0f && span.from == 0f))
            drawRoundRect(
                if (lit) palette.accent else palette.line,
                topLeft = Offset(span.from * size.width + gap / 2, middle - track / 2),
                size = Size(((span.until - span.from) * size.width - gap).coerceAtLeast(0f), track),
                cornerRadius = CornerRadius(track / 2),
            )
        }
        val markerWidth = Sizes.demoMarkerWidth.toPx()
        val markerHeight = Sizes.demoMarkerHeight.toPx()
        val x = (now * size.width).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
        drawRoundRect(
            palette.text,
            topLeft = Offset(x - markerWidth / 2, middle - markerHeight / 2),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerWidth / 2),
        )
    }
}

/** How far through a rep a touch at [x] along a track [width] wide is. */
private fun placeAt(x: Float, width: Int): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)

/** How far inside a stretch a step lands, and how near its start still counts as there. */
private const val STEP_SLACK = 0.001f
