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
import androidx.compose.ui.graphics.Color

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
