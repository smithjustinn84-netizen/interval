package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.IntervalTheme

/**
 * [values] as a line, oldest first, spread evenly across the width and scaled from [min] at the bottom to [max] at
 * the top (all at mid height when they are equal): the first point a quiet dot, the latest one in the accent. An
 * exercise's best each session on its page. It reads as one node, [description] its words for a screen reader.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    min: Float,
    max: Float,
    description: String,
    modifier: Modifier = Modifier,
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
                val span = max - min
                val step = if (values.size > 1) (size.width - 2 * dot) / (values.size - 1) else 0f
                val points = values.mapIndexed { i, value ->
                    val share = if (span > 0f) (value - min) / span else HALF
                    Offset(dot + i * step, dot + (size.height - 2 * dot) * (1 - share))
                }
                val path = Path().apply {
                    points.firstOrNull()?.let { moveTo(it.x, it.y) }
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                onDrawBehind {
                    if (points.size > 1) drawPath(path, palette.accent, style = stroke)
                    points.firstOrNull()?.let { drawCircle(palette.textDim, dot, it) }
                    points.lastOrNull()?.let { drawCircle(palette.accent, dot, it) }
                }
            },
    )
}

private const val HALF = 0.5f
