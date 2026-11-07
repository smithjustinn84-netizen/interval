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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Density
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.lift.ui.LiftSizes

/**
 * [demo]'s rep laid out as a bar, a stretch for each phase in its beat's colour, with a marker where [player] is;
 * a demo that sets up first has its set-up ahead of the rep, apart from it, in the set-up beat's colour.
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
    val bar = DemoBar(demo, LocalDensity.current)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Sizes.iconButton)
            .pointerInput(demo, player) { detectTapGestures { player.seek(bar.placeAt(it.x, size.width.toFloat())) } }
            .pointerInput(demo, player) {
                detectHorizontalDragGestures(
                    onDragStart = { player.seek(bar.placeAt(it.x, size.width.toFloat())) },
                ) { change, _ ->
                    change.consume()
                    player.seek(bar.placeAt(change.position.x, size.width.toFloat()))
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val starts = listOfNotNull(demo.setUp?.let { ExerciseDemo.SETTING_UP }) + stretches.map { it.first }
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
                progressBarRangeInfo = ProgressBarRangeInfo(player.progress, demo.earliest..1f)
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
        fun stretch(from: Float, to: Float, beat: DemoBeat) = drawRoundRect(
            colors.of(beat),
            topLeft = Offset(from + gap / 2, middle - track / 2),
            size = Size((to - from - gap).coerceAtLeast(0f), track),
            cornerRadius = round,
        )
        if (demo.setUp != null) stretch(0f, bar.setUpWidth(size.width), DemoBeat.Set)
        stretches.forEach { (start, phase) ->
            stretch(bar.xOf(start, size.width), bar.xOf(phase.until.coerceAtMost(1f), size.width), phase.beat)
        }
        val markerWidth = LiftSizes.demoMarkerWidth.toPx()
        val markerHeight = LiftSizes.demoMarkerHeight.toPx()
        val x = bar.xOf(player.progress, size.width).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
        drawRoundRect(
            marker,
            topLeft = Offset(x - markerWidth / 2, middle - markerHeight / 2),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerWidth / 2),
        )
    }
}

/**
 * Where on a bar [width] wide each place in [demo] is drawn: a set-up first, its share of the bar as long as it
 * takes beside a rep (no narrower than a set-up's least), then a gap, then the rep over the rest.
 */
internal class DemoBar(private val demo: ExerciseDemo, density: Density) {
    private val least = with(density) { LiftSizes.demoSetUpLeast.toPx() }
    private val apart = with(density) { LiftSizes.demoSetUpGap.toPx() }

    fun setUpWidth(width: Float): Float {
        val setUp = demo.setUp ?: return 0f
        return maxOf(width * setUp.seconds / (setUp.seconds + demo.secondsPerRep), least)
    }

    private fun repStart(width: Float) = if (demo.setUp == null) 0f else setUpWidth(width) + apart

    /** Where [at] is drawn across the bar. */
    fun xOf(at: Float, width: Float): Float {
        if (at < 0f) return (at - ExerciseDemo.SETTING_UP) * setUpWidth(width)
        val start = repStart(width)
        return start + at * (width - start)
    }

    /** The place in the demo drawn at [x]: setting up up to halfway across the gap, the rep beyond. */
    fun placeAt(x: Float, width: Float): Float {
        val setUp = setUpWidth(width)
        if (demo.setUp != null && x < setUp + apart / 2) {
            return (ExerciseDemo.SETTING_UP + x / setUp).coerceIn(ExerciseDemo.SETTING_UP, -SET_UP_END)
        }
        val start = repStart(width)
        return ((x - start) / (width - start)).coerceIn(0f, 1f)
    }
}

/** A place picked on the set-up stays this short of its end, so it shows the set-up rather than the rep. */
private const val SET_UP_END = 0.01f

/** A step by key goes to a stretch's start beyond this close to where it is, so it never stays put. */
private const val STEP_SLACK = 0.001f
