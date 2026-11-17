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
import dev.justinsmith.interval.ui.IntervalTheme

/**
 * A line through [heights], oldest first, spread evenly across the width, each from 0 at the bottom to 1 at the top:
 * the first point a quiet dot, the latest one in the accent, and each point in [marks], by its place, a dashed rule
 * top to bottom with a quiet dot where the line starts again (a lift gone to another bell, whose stretch is scaled
 * on its own). An exercise's best each session on its page. It reads as one node, [description] its words for a
 * screen reader.
 */
@Composable
fun Sparkline(
    heights: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    marks: List<Int> = emptyList(),
) {
    val palette = IntervalTheme.palette
    Spacer(
        modifier
            .fillMaxWidth()
            .height(LiftSizes.sparkline)
            .semantics { contentDescription = description }
            .drawWithCache {
                val dot = LiftSizes.sparklineDot.toPx() / 2
                val stroke = Stroke(
                    width = LiftSizes.sparklineStroke.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val step = if (heights.size > 1) (size.width - 2 * dot) / (heights.size - 1) else 0f
                val points = heights.mapIndexed { i, height ->
                    Offset(dot + i * step, dot + (size.height - 2 * dot) * (1 - height))
                }
                val path = Path().apply {
                    points.forEachIndexed { i, point ->
                        if (i == 0 || i in marks) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                val marked = marks.mapNotNull { points.getOrNull(it) }
                val dash = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
                val rule = LiftSizes.sparklineMark.toPx()
                onDrawBehind {
                    marked.forEach {
                        drawLine(palette.line, Offset(it.x, 0f), Offset(it.x, size.height), rule, pathEffect = dash)
                    }
                    if (points.size > 1) drawPath(path, palette.accent, style = stroke)
                    points.firstOrNull()?.let { drawCircle(palette.textDim, dot, it) }
                    marked.forEach { drawCircle(palette.textDim, dot, it) }
                    points.lastOrNull()?.let { drawCircle(palette.accent, dot, it) }
                }
            },
    )
}

private const val DASH = 8f
