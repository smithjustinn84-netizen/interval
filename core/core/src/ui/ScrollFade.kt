package dev.justinsmith.interval.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades the foot of a column scrolled by [scroll] while more of it lies below, so what is cut off reads as there:
 * a heading left alone above a pinned footer says its card is under it. Both sides' summaries wear it, before
 * their `verticalScroll`. The fade is [Sizes.summaryFade] tall, shrinking over the last of the way so it is gone at
 * the end. Read as it draws, so scrolling only redraws. The column is drawn apart (offscreen, which the fade's
 * blend needs) only while there is a fade, and the fade is one gradient made as the size is, squeezed to the way
 * left.
 */
fun Modifier.fadeWhenMoreBelow(scroll: ScrollState): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (scroll.canScrollForward) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithCache {
        val full = Sizes.summaryFade.toPx()
        val fadeBrush = Brush.verticalGradient(FadeColors, startY = 0f, endY = full)
        val fadeSize = Size(size.width, full)
        onDrawWithContent {
            drawContent()
            val fade = minOf(full, (scroll.maxValue - scroll.value).toFloat())
            if (fade > 0f) {
                translate(top = size.height - full) {
                    scale(scaleX = 1f, scaleY = fade / full, pivot = Offset(0f, full)) {
                        drawRect(fadeBrush, size = fadeSize, blendMode = BlendMode.DstIn)
                    }
                }
            }
        }
    }

/** The fade, opaque to clear: what [fadeWhenMoreBelow] keeps of the column, down its foot. */
private val FadeColors = listOf(Color.Black, Color.Transparent)
