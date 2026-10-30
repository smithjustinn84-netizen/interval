package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * One bar of [Bars]: its [value] against the chart's top. [filled] bars are in the accent, the rest in the quiet
 * surface; an [outlined] bar is one still going (this week), drawn as its outline.
 */
data class Bar(val value: Float, val filled: Boolean = true, val outlined: Boolean = false)

/**
 * Bars to scale, bottoms aligned: History's workouts a week, an exercise's best a session. [max] is the top
 * of the chart; a dashed [line] marks that value across it when given (a goal). The row reads as one node,
 * [description] its words for a screen reader, so the bars themselves say nothing.
 */
@Composable
fun Bars(
    bars: List<Bar>,
    max: Float,
    description: String,
    modifier: Modifier = Modifier,
    line: Float? = null,
) {
    val palette = IntervalTheme.palette
    val top = max.coerceAtLeast(MIN_MAX)
    Row(
        modifier
            .fillMaxWidth()
            .height(Sizes.weeksChart)
            .semantics { contentDescription = description }
            .drawBehind {
                if (line != null) {
                    val y = size.height * (1 - line / top)
                    drawLine(
                        palette.line,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = Sizes.border.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                    )
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar -> BarBox(bar, top) }
    }
}

@Composable
private fun RowScope.BarBox(bar: Bar, top: Float) {
    val palette = IntervalTheme.palette
    val height = (Sizes.weeksChart * (bar.value / top)).coerceAtLeast(Sizes.weekBarEmpty)
    val shape = Modifier.weight(1f).height(height).clip(Corners.radius12)
    Box(
        when {
            bar.outlined -> shape.border(Sizes.border, palette.accent, Corners.radius12)
            bar.filled && bar.value > 0f -> shape.background(palette.accent)
            else -> shape.background(palette.surfaceVariant)
        },
    )
}

private const val DASH = 12f
private const val MIN_MAX = 1f
