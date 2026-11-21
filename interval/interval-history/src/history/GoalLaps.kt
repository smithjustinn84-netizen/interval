package dev.justinsmith.interval.history

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.justinsmith.interval.ui.Angles
import kotlin.math.PI

/** [share] of the goal as one arc from twelve o'clock in [fill], its sweep turned to start there too. */
internal fun DrawScope.drawGoalArc(share: Float, fill: Brush, topLeft: Offset, arcSize: Size, stroke: Stroke) {
    if (share <= 0f) return
    rotate(Angles.TWELVE_O_CLOCK) {
        drawArc(fill, 0f, Angles.FULL_TURN * share, false, topLeft, arcSize, style = stroke)
    }
}

/**
 * [goal] laps round the ring, a workout each with [gapPx] between, on [track], each filled in [fill] as far as
 * [share] of the goal has come round.
 */
internal fun DrawScope.drawGoalLaps(
    goal: Int,
    share: Float,
    topLeft: Offset,
    arcSize: Size,
    stroke: Stroke,
    gapPx: Float,
    track: Color,
    fill: Brush,
) {
    val each = Angles.FULL_TURN / goal
    val gap = gapPx / (PI.toFloat() * arcSize.width) * Angles.FULL_TURN
    repeat(goal) { index ->
        val start = Angles.TWELVE_O_CLOCK + each * index + gap / 2
        val sweep = each - gap
        drawArc(track, start, sweep, false, topLeft, arcSize, style = stroke)
        val filled = (share * goal - index).coerceIn(0f, 1f)
        if (filled > 0f) drawArc(fill, start, sweep * filled, false, topLeft, arcSize, style = stroke)
    }
}
