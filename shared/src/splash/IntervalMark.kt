package dev.justinsmith.interval.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.PhaseColors
import kotlin.math.abs

/**
 * The app's mark, as the launcher icons and launch screens show it: the workout's phase ring as three
 * round-capped arcs (Get ready, Work, Rest), with a clock hand pointing up through the gap at the top.
 *
 * Measured on Android's 108-unit adaptive-icon canvas, in the same numbers as
 * `tools/generate_icons.py`, which draws the icons. Keep the two in step. Angles are degrees clockwise
 * from 12 o'clock.
 */
internal object Mark {
    const val CANVAS = 108f
    const val RING_RADIUS = 24f
    const val RING_STROKE = 7f
    const val HAND_LENGTH = 15f
    const val HAND_STROKE = 4.5f
    const val HUB_RADIUS = 4.5f
    const val PIN_RADIUS = 1.7f

    val arcs = listOf(
        MarkArc(start = 14f, end = 64f, color = PhaseColors.prep),
        MarkArc(start = 93f, end = 227f, color = PhaseColors.work),
        MarkArc(start = 256f, end = 346f, color = PhaseColors.rest),
    )
}

internal data class MarkArc(val start: Float, val end: Float, val color: Color) {
    val middle: Float get() = (start + end) / 2
}

/**
 * Draws the mark centred on [center] at [unit] pixels per canvas unit. [handTurns] turns the hand
 * clockwise from 12, and each arc swells as the hand sweeps past its middle; at whole turns the
 * mark is the icon exactly.
 */
internal fun DrawScope.drawIntervalMark(center: Offset, unit: Float, handTurns: Float = 0f, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val handAngle = handTurns * Angles.FULL_TURN
    for (arc in Mark.arcs) {
        val swell = swell(handAngle, arc.middle)
        val radius = (Mark.RING_RADIUS + SWELL_OUTWARD * swell) * unit
        drawArc(
            arc.color,
            startAngle = arc.start - Angles.QUARTER_TURN,
            sweepAngle = arc.end - arc.start,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            alpha = alpha,
            style = Stroke(Mark.RING_STROKE * (1 + SWELL_THICKER * swell) * unit, cap = StrokeCap.Round),
        )
    }
    rotate(handAngle, pivot = center) {
        drawLine(
            PhaseColors.onGround,
            start = Offset(center.x, center.y - Mark.HUB_RADIUS * unit),
            end = Offset(center.x, center.y - Mark.HAND_LENGTH * unit),
            strokeWidth = Mark.HAND_STROKE * unit,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
    // The hub is a ring, so the pin hole shows whatever is behind the mark, as on the icons.
    drawCircle(
        PhaseColors.onGround,
        radius = (Mark.HUB_RADIUS + Mark.PIN_RADIUS) / 2 * unit,
        center = center,
        alpha = alpha,
        style = Stroke((Mark.HUB_RADIUS - Mark.PIN_RADIUS) * unit),
    )
}

/** 0 to 1 as the hand nears an arc's [middle]: a smooth bump [SWELL_REACH] degrees to each side. */
private fun swell(handAngle: Float, middle: Float): Float {
    val apart = abs((handAngle - middle).mod(Angles.FULL_TURN) - Angles.HALF_TURN).let { Angles.HALF_TURN - it }
    return FastOutSlowInEasing.transform((1 - apart / SWELL_REACH).coerceAtLeast(0f))
}

/**
 * How far either side of an arc's middle the passing hand swells it, in degrees. Short of 12
 * o'clock, so the hand at rest leaves the mark exactly the icon.
 */
private const val SWELL_REACH = 36f

/** At its fullest an arc is this much thicker, and sits this many canvas units further out. */
private const val SWELL_THICKER = 0.8f
private const val SWELL_OUTWARD = 2.5f
