package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.justinsmith.interval.ui.IntervalTheme

/**
 * A line through [heights], oldest first, spread evenly across the width, each from 0 at the bottom to 1 at the top:
 * the first point a quiet dot, the latest one in the accent, and each point in [marks], by its place, a dashed rule
 * top to bottom with a quiet dot where the line starts again (a lift gone to another bell, whose stretch is scaled
 * on its own), and each of [labels], one a stretch from the first point and then each mark's, over its stretch's
 * start in a band above the line: its whole text where that ends before the next stretch, else its short one (left
 * out if even that would run into the label before it). An exercise's best each session on its page. It reads as one
 * node, [description] its words for a screen reader.
 */
@Composable
fun Sparkline(
    heights: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    marks: List<Int> = emptyList(),
    labels: List<SparklineLabel> = emptyList(),
    rings: List<Int> = emptyList(),
) {
    val palette = IntervalTheme.palette
    val measurer = rememberTextMeasurer()
    val labelStyle = IntervalTheme.type.caption.copy(color = palette.textDim)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(if (labels.isEmpty()) LiftSizes.sparkline else LiftSizes.sparkline + LiftSizes.sparklineLabels)
            .semantics { contentDescription = description }
            .drawWithCache {
                val dot = LiftSizes.sparklineDot.toPx() / 2
                val stroke = Stroke(
                    width = LiftSizes.sparklineStroke.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val top = if (labels.isEmpty()) 0f else LiftSizes.sparklineLabels.toPx()
                val step = if (heights.size > 1) (size.width - 2 * dot) / (heights.size - 1) else 0f
                val points = heights.mapIndexed { i, height ->
                    Offset(dot + i * step, top + dot + (size.height - top - 2 * dot) * (1 - height))
                }
                val placed = placeLabels(labels, listOf(0) + marks, points, size.width) {
                    measurer.measure(it, labelStyle)
                }
                val path = Path().apply {
                    points.forEachIndexed { i, point ->
                        if (i == 0 || i in marks) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                val marked = marks.mapNotNull { points.getOrNull(it) }
                val ringed = rings.mapNotNull { points.getOrNull(it) }
                val ring = Stroke(width = LiftSizes.sparklineMark.toPx())
                val dash = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
                val rule = LiftSizes.sparklineMark.toPx()
                onDrawBehind {
                    marked.forEach {
                        drawLine(palette.line, Offset(it.x, 0f), Offset(it.x, size.height), rule, pathEffect = dash)
                    }
                    placed.forEach { (text, at) -> drawText(text, topLeft = at) }
                    if (points.size > 1) drawPath(path, palette.accent, style = stroke)
                    points.firstOrNull()?.let { drawCircle(palette.textDim, dot, it) }
                    marked.forEach { drawCircle(palette.textDim, dot, it) }
                    points.lastOrNull()?.let { drawCircle(palette.accent, dot, it) }
                    ringed.forEach { drawCircle(palette.accent, dot * RING, it, style = ring) }
                }
            },
    )
}

/** A stretch's label: the first of [texts], longest first, that has room, else the last. */
data class SparklineLabel(val texts: List<String>)

/**
 * Each label over its stretch's start, by [starts], the place of each stretch's first point: the first of its texts
 * that ends before the next stretch starts (the last before the width), else its shortest, kept inside the width and
 * left out where it would run into the one before.
 */
private fun placeLabels(
    labels: List<SparklineLabel>,
    starts: List<Int>,
    points: List<Offset>,
    width: Float,
    measure: (String) -> TextLayoutResult,
): List<Pair<TextLayoutResult, Offset>> {
    var end = 0f
    return labels.zip(starts).mapIndexedNotNull { i, (label, start) ->
        val point = points.getOrNull(start)
        if (point == null || label.texts.isEmpty()) return@mapIndexedNotNull null
        val room = starts.getOrNull(i + 1)?.let { points.getOrNull(it)?.x } ?: width
        val texts = label.texts.asSequence().map(measure)
        val text = texts.firstOrNull { point.x + it.size.width + it.size.height <= room } ?: texts.last()
        val x = point.x.coerceAtMost(width - text.size.width).coerceAtLeast(0f)
        if (end > 0f && x < end) return@mapIndexedNotNull null
        end = x + text.size.width + text.size.height
        text to Offset(x, 0f)
    }
}

private const val DASH = 8f
private const val RING = 1.8f
