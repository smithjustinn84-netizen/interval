package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

// The design system's Motion section, in code: its easings and springs, and the few moves every
// screen makes (a press, an arrival, a value changing in place). Durations are the Motion tokens.

/** Material's emphasized decelerate: arrives fast and settles slowly. For anything entering. */
val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Material's emphasized accelerate: leaves without lingering. For anything exiting. */
val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** For content changing in place, and for colours. */
val StandardEasing: Easing = FastOutSlowInEasing

/** The spring under a pressed control: stiff, with just enough give to feel physical. */
fun pressSpring(): SpringSpec<Float> = spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS)

/** A selection indicator sliding to its new option: quick, settling without a bounce. */
fun <T> settleSpring(): SpringSpec<T> = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessMedium)

private const val PRESS_DAMPING = 0.7f
private const val PRESS_STIFFNESS = 1100f
private const val SETTLE_DAMPING = 0.9f

/** A rising entrance starts this share of the content's height below its place. */
private const val RISE_SHARE = 0.25f

/** A rolling value travels this share of its height. */
private const val ROLL_SHARE = 0.4f

/**
 * Shrinks a control to [Motion.PRESS_SCALE] while [interaction] reports it pressed, springing back
 * on release: the touch feedback every button and stepper shares, under its ripple.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.PRESS_SCALE else 1f, pressSpring(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Hidden on its first frame, then shown: `AnimatedVisibility(visibleState = rememberEntrance())`
 * plays its enter transition once, as the content first appears, and never exits.
 */
@Composable
fun rememberEntrance(): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(false).apply { targetState = true } }

/** Something arriving: it fades in as it rises from a little below its place, after [delayMillis]. */
fun enterRising(delayMillis: Int = 0): EnterTransition {
    val spec = tween<Float>(Motion.ENTER_MILLIS, delayMillis, EnterEasing)
    return fadeIn(spec) +
        slideInVertically(tween(Motion.ENTER_MILLIS, delayMillis, EnterEasing)) { (it * RISE_SHARE).toInt() }
}

/** Something arriving in place, such as a dialog: it fades in as it grows from a touch smaller. */
fun enterGrowing(delayMillis: Int = 0): EnterTransition =
    fadeIn(tween(Motion.ENTER_MILLIS, delayMillis)) +
        scaleIn(tween(Motion.ENTER_MILLIS, delayMillis, EnterEasing), initialScale = Motion.FADE_THROUGH_SCALE)

/** The reverse of [enterGrowing], quicker. */
fun exitShrinking(): ExitTransition =
    fadeOut(tween(Motion.EXIT_MILLIS)) +
        scaleOut(tween(Motion.EXIT_MILLIS, easing = ExitEasing), targetScale = Motion.FADE_THROUGH_SCALE)

/**
 * Text whose value changes in place, like a counter's drum: the old value slips away as the new
 * one slides in, from below when the value is [rising] and from above when it falls. The node
 * grows or shrinks with the text, unclipped, so the digits can overshoot it.
 */
@Composable
fun RollingText(
    text: String,
    rising: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val direction = if (rising) 1 else -1
            val enter = fadeIn(tween(Motion.CONTENT_MILLIS)) +
                slideInVertically(tween(Motion.CONTENT_MILLIS, easing = StandardEasing)) {
                    (it * ROLL_SHARE).toInt() * direction
                }
            val exit = fadeOut(tween(Motion.EXIT_MILLIS)) +
                slideOutVertically(tween(Motion.EXIT_MILLIS, easing = StandardEasing)) {
                    -(it * ROLL_SHARE).toInt() * direction
                }
            enter.togetherWith(exit).using(SizeTransform(clip = false))
        },
        label = "rollingText",
    ) { value ->
        Text(value, style = style, color = color)
    }
}
