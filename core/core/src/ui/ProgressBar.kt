package dev.justinsmith.interval.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A bar filled to [share] (0 to 1) in [color] over a quiet track, growing to each new share. It says nothing to a
 * screen reader: the words beside it carry the figures.
 */
@Composable
fun ProgressBar(share: Float, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(share.coerceIn(0f, 1f), tween(Motion.ENTER_MILLIS, easing = EnterEasing))
    Spacer(
        modifier
            .fillMaxWidth()
            .height(Sizes.progressBar)
            .clip(Corners.full)
            .background(IntervalTheme.palette.surfaceVariant)
            .drawBehind {
                if (shown > 0f) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color, size = size.copy(width = size.width * shown), cornerRadius = radius)
                }
            },
    )
}

/**
 * A run of [count] laps, a short bar each, the first [done] filled in [color], the one under way after them
 * ([current]) outlined in it, the rest dim: a program's days as a track rather than a bar. Past [MAX_LAPS] a lap
 * would be too thin to see, so it draws a [ProgressBar] of the same share instead. Decorative: its caller says
 * where it stands in words.
 */
@Composable
fun LapBar(count: Int, done: Int, color: Color, modifier: Modifier = Modifier, current: Boolean = true) {
    if (count > MAX_LAPS || count <= 0) {
        ProgressBar(if (count <= 0) 0f else done.toFloat() / count, color, modifier)
        return
    }
    val upcoming = IntervalTheme.palette.surfaceVariant
    Spacer(
        modifier
            .fillMaxWidth()
            .height(Sizes.lapBar)
            .drawBehind {
                val gap = Sizes.lapGap.toPx()
                val each = (size.width - gap * (count - 1)) / count
                val radius = CornerRadius(Sizes.lapGap.toPx())
                val outline = Stroke(Sizes.hairline.toPx() * 2)
                repeat(count) { index ->
                    val left = index * (each + gap)
                    val lap = Size(each, size.height)
                    when {
                        index < done -> drawRoundRect(color, Offset(left, 0f), lap, radius)

                        index == done && current -> {
                            val inset = outline.width / 2
                            drawRoundRect(
                                color,
                                Offset(left + inset, inset),
                                Size(each - outline.width, size.height - outline.width),
                                radius,
                                style = outline,
                            )
                        }

                        else -> drawRoundRect(upcoming, Offset(left, 0f), lap, radius)
                    }
                }
            },
    )
}

/** The most laps a [LapBar] draws one by one; a longer run is a [ProgressBar]. */
const val MAX_LAPS = 60
