package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

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
    val scale = remember { Animatable(1f) }
    LaunchedEffect(intervalKey) {
        scale.snapTo(Motion.CLOCK_POP_SCALE)
        scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
    }
    LaunchedEffect(text, countingDown) {
        if (countingDown) {
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
