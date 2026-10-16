package dev.justinsmith.interval.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ticks round a Dial: one a second, like a watch face. */
const val DIAL_TICKS = 60

/** Every fifth tick is a long one. */
private const val LONG_TICK_EVERY = 5

/** The lit tick and its fading trail behind the Timer total, newest first. */
private val TrailAlphas = listOf(0.7f, 0.4f, 0.22f, 0.1f)

private const val FULL_TURN = 2 * PI
private const val QUARTER_TURN = PI / 2

/** How many of the ring's ticks are lit at [progress] through an interval: they fill a second's worth at a time. */
fun litTicks(progress: Float): Int = (progress.coerceIn(0f, 1f) * DIAL_TICKS).toInt()

/** The tick the Dial's hand is on after [elapsedMillis]: once round a minute, a tick a second. */
fun dialTick(elapsedMillis: Long): Int = ((elapsedMillis / Motion.DIAL_STEP_MILLIS) % DIAL_TICKS).toInt()

/**
 * Draws [DIAL_TICKS] ticks round [center], clockwise from twelve o'clock, each from [inner] (or
 * [innerLong] for every fifth) out to [outer]. [color] picks each tick's colour, or null to skip it.
 */
internal fun DrawScope.drawTicks(
    center: Offset,
    inner: Float,
    innerLong: Float,
    outer: Float,
    stroke: Float,
    color: (Int) -> Color?,
) {
    repeat(DIAL_TICKS) { index ->
        val tint = color(index) ?: return@repeat
        val angle = index * FULL_TURN / DIAL_TICKS - QUARTER_TURN
        val from = if (index % LONG_TICK_EVERY == 0) innerLong else inner
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(tint, center + direction * from, center + direction * outer, stroke, StrokeCap.Round)
    }
}

/**
 * The Dial behind the Timer tab's total: a watch face of quiet ticks, half off the column's end,
 * with one tick lit in the accent stepping on each second and a short trail fading behind it. It is
 * artwork only, under the cards; with reduced motion on, the hand rests at twelve.
 */
@Composable
fun Modifier.tickDialBehind(): Modifier {
    val palette = IntervalTheme.palette
    val hand = rememberDialHand()
    return drawBehind {
        val center = Offset(size.width - Sizes.dialCentreInset.toPx(), Sizes.dialCentreTop.toPx())
        val inner = Sizes.dialInner.toPx()
        val innerLong = Sizes.dialInnerLong.toPx()
        val outer = Sizes.dialOuter.toPx()
        drawTicks(center, inner, innerLong, outer, Sizes.dialStroke.toPx()) { palette.dialTick }
        val lit = hand.value
        drawTicks(center, innerLong, innerLong, outer, Sizes.dialHandStroke.toPx()) { index ->
            val behind = (lit - index + DIAL_TICKS) % DIAL_TICKS
            TrailAlphas.getOrNull(behind)?.let { palette.accent.copy(alpha = it) }
        }
    }
}

/**
 * The tick the hand is on, stepping once a second. Read only while drawing, so a step redraws the
 * Dial without recomposing anything. The frame clock is read as an infinite animation, so UI tests
 * (which cancel those) never wait on it.
 */
@Composable
private fun rememberDialHand(): State<Int> {
    val tick = remember { mutableIntStateOf(0) }
    val still = prefersReducedMotion()
    LaunchedEffect(still) {
        tick.intValue = 0
        if (still) return@LaunchedEffect
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            tick.intValue = dialTick(elapsed)
            delay(Motion.DIAL_STEP_MILLIS - elapsed % Motion.DIAL_STEP_MILLIS)
        }
    }
    return tick
}
