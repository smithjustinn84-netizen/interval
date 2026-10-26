package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.justinsmith.interval.platform.prefersReducedMotion

/**
 * A countdown's digits ([text]) on the workout's ground. The clock pops as a new interval starts (a
 * change of [intervalKey]) and, smaller, on each of its last seconds while [countingDown], in step
 * with the beeps, when its colour also goes to the [accent]; otherwise it reads in the ground's ink.
 */
@Composable
fun CountdownClock(
    text: String,
    intervalKey: Any?,
    countingDown: Boolean,
    accent: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = prefersReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(intervalKey, reducedMotion) {
        if (!reducedMotion) {
            scale.snapTo(Motion.CLOCK_POP_SCALE)
            scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
        } else {
            scale.snapTo(1f)
        }
    }
    LaunchedEffect(text, countingDown, reducedMotion) {
        if (countingDown && !reducedMotion) {
            scale.snapTo(Motion.COUNTDOWN_POP_SCALE)
            scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
        }
    }
    val color by animateColorAsState(
        if (countingDown) accent else IntervalTheme.workout.onGround,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "clock",
    )
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

/**
 * The clock fills the share of the ring its digits allow, up to [max], the clock style's size: "0:14"
 * in a phone's ring comes out around 160sp, as the design canvas draws it, and "10:00" smaller.
 * Converted through [LocalDensity] so large system font scales never overflow the ring's geometry.
 */
@Composable
@ReadOnlyComposable
fun ringClockSize(diameter: Dp, chars: Int, max: TextUnit): TextUnit = with(LocalDensity.current) {
    val target = diameter * RING_CLOCK_SHARE / (chars * CLOCK_EMS_PER_CHAR)
    minOf(target.toPx(), max.toPx()).toSp()
}

/** How much of the ring's width the clock may take, leaving the stroke and some air. */
private const val RING_CLOCK_SHARE = 0.82f

/** Width per clock character in Barlow Condensed ExtraBold. */
private const val CLOCK_EMS_PER_CHAR = 0.46f
